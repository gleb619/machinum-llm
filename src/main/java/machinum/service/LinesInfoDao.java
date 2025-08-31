package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.model.Book;
import machinum.model.CheckedFunction;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static machinum.model.CheckedConsumer.checked;
import static machinum.util.JavaUtil.md5;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinesInfoDao {

    public static final int MAX_CHAPTERS_PER_VIEW = 300;
    public static final int SMALL_BOOK_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final AsyncHelper asyncHelper;

    @Qualifier("linesMapper")
    private final Holder<ObjectMapper> objectMapper;


    @Transactional
    public void initializeBookSettings(String bookId) {
        log.debug("Initializing book settings for bookId: {}", bookId);

        var bookTitle = jdbcTemplate.queryForObject("SELECT title FROM books WHERE id = ?", String.class, bookId);

        var defaultSettings = BookSettings.builder()
                .maxChaptersPerView(MAX_CHAPTERS_PER_VIEW)
                .minChaptersForSplit(SMALL_BOOK_SIZE)
                .templateName(escapeObjectName(bookTitle))
                .viewNames(new ArrayList<>())
                .build();

        var configs = calculateViewConfigs(bookId, defaultSettings);
        var viewNames = configs.stream()
                .map(ViewConfig::getViewName)
                .toList();

        defaultSettings.setViewNames(viewNames);

        var dataJson = objectMapper.execute(mapper -> mapper.writeValueAsString(defaultSettings));
        var hash = calculateHash(bookId, defaultSettings);

        jdbcTemplate.update(
                """
                        INSERT INTO line_settings (book_id, name, data, settings_hash) \
                        VALUES (?, 'book_config', ?::jsonb, ?) \
                        ON CONFLICT (book_id, name) DO UPDATE SET \
                        data = ?::jsonb, settings_hash = ?, updated_at = CURRENT_TIMESTAMP""",
                bookId, dataJson, hash, dataJson, hash
        );
    }

    @Transactional
    public int createMatViews(String bookId) {
        log.debug("Processing views for book {}", bookId);

        int output = 1;
        var settings = getBookSettings(bookId);
        var configs = calculateViewConfigs(bookId, settings);
        // Drop old views not in new config
        var newViewNames = configs.stream()
                .map(ViewConfig::getViewName)
                .toList();

        if (!isSettingsChanged(bookId, settings.toBuilder()
                .viewNames(newViewNames)
                .build())) {
            log.debug("Settings unchanged for book {}, skipping", bookId);
            return 0;
        }

        settings.getViewNames().stream()
                .filter(oldView -> !newViewNames.contains(oldView))
                .forEach(this::dropSingleView);

        // Create/update views
        for (var config : configs) {
            //TODO add some hash to name, to fix problem with range. Like you create from 1 to 400,
            //TODO then added another 400 chapters, but existed view, doesn't recreated
            if (!viewExists(config.getViewName())) {
                boolean created = createSingleSubView(config, bookId);
                log.debug("View {} creation: {}", config.getViewName(), created ? "success" : "failed");
                if (!created) {
                    output = 0;
                }
            }
        }

        // Update settings with new view names
        settings.setViewNames(newViewNames);
        updateBookSettings(bookId, settings);

        return output;
    }

    @Transactional
    public void updateBookSettings(String bookId, BookSettings settings) {
        log.trace("Updating book settings for bookId: {}", bookId);

        try {
            var dataJson = objectMapper.execute(mapper -> mapper.writeValueAsString(settings));
            var hash = calculateHash(bookId, settings);

            jdbcTemplate.update(
                    """
                            UPDATE line_settings SET data = ?::jsonb, settings_hash = ?, updated_at = CURRENT_TIMESTAMP \
                            WHERE book_id = ? AND name = 'book_config'""",
                    dataJson, hash, bookId
            );
        } catch (Exception e) {
            log.error("Error updating settings for book {}", bookId, e);
            throw new RuntimeException("Failed to update book settings", e);
        }
    }

    @Transactional
    public void recreateLinesInfoView(List<String> bookIds) {
        log.debug("Recreating lines info view for {} books", bookIds.size());

        // Recreate main view with all book views
        var allViewNames = bookIds.stream()
                .map(this::getBookSettings)
                .flatMap(settings -> settings.getViewNames().stream())
                .toList();

        createLinesInfoView(allViewNames);
    }

    @Transactional
    public boolean refreshView(String bookId, Integer chapterNumber) {
        return refreshView(bookId, List.of(chapterNumber));
    }

    @Transactional
    public boolean refreshView(String bookId, List<Integer> chapterNumbers) {
        log.debug("Refreshing view for: bookId={}, chapterNumbers={}", bookId, chapterNumbers.size());

        // Get current book settings
        var settings = getBookSettings(bookId);

        // Parse view names to refresh and make them unique
        var uniqueViewNames = chapterNumbers.stream()
                .map(chapterNumber -> parseViewName(bookId, chapterNumber, settings))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (uniqueViewNames.isEmpty()) {
            log.warn("Can't find any views to refresh: bookId={}, chapterNumbers={}", bookId, chapterNumbers);
            return false;
        }

        if (uniqueViewNames.size() > 1) {
            // Refresh all unique views in parallel
            var futures = uniqueViewNames.stream()
                    .map(viewName -> asyncHelper.inNewTransaction(() -> refreshSingleViewConcurrently(viewName)))
                    .toList();
            log.trace("Refreshing {} views, awaiting result", futures.size());

            // Wait for all to complete and check if all succeeded
            boolean result = futures.stream()
                    .map(CompletableFuture::join)
                    .reduce(true, (a, b) -> a && b);

//            // Call refreshSingleView for each viewName asynchronously
//            uniqueViewNames.forEach(viewName -> asyncHelper.inNewTransaction(() -> refreshSingleView(viewName)));

            return result;
        } else {
            String viewName = uniqueViewNames.iterator().next();
            boolean result = refreshSingleViewConcurrently(viewName);
//            // Call refreshSingleView for the viewName asynchronously
//            asyncHelper.inNewTransaction(() -> refreshSingleView(viewName));
            return result;
        }
    }

    @Transactional
    public BookSettings findOrCreateSettings(String bookId) {
        log.trace("Prepare to find or create book settings for bookId: {}", bookId);

        try {
            return getBookSettings(bookId);
        } catch (Exception e) {
            log.warn("No settings found for book {}, initializing defaults", bookId);
            initializeBookSettings(bookId);
            return getBookSettings(bookId);
        }
    }

    @Transactional(readOnly = true)
    public BookSettings getBookSettings(String bookId) {
        log.trace("Getting book settings for bookId: {}", bookId);

        var result = jdbcTemplate.queryForMap(
                "SELECT data FROM line_settings WHERE book_id = ? AND name = 'book_config'",
                bookId
        );
        var dataJson = result.get("data").toString();
        return objectMapper.execute(mapper -> mapper.readValue(dataJson, BookSettings.class));
    }

    /* ============= */

    private boolean isSettingsChanged(String bookId, BookSettings newSettings) {
        log.trace("Checking if settings changed for bookId: {}", bookId);

        try {
            var result = jdbcTemplate.queryForMap(
                    "SELECT data, settings_hash FROM line_settings WHERE book_id = ? AND name = 'book_config'",
                    bookId
            );

            var currentHash = result.get("settings_hash").toString();
            var calculatedHash = calculateHash(bookId, newSettings);

            return !currentHash.equals(calculatedHash);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean createSingleSubView(ViewConfig config, String bookId) {
        log.trace("Creating single sub view for bookId: {}, viewName: {}", bookId, config.getViewName());

        Boolean result;
        if (config.getStartChapter() != null && config.getEndChapter() != null) {
            result = jdbcTemplate.queryForObject(
                    "SELECT create_single_sub_view(?, ?, ?, ?)",
                    Boolean.class,
                    config.getViewName(), bookId, config.getStartChapter(), config.getEndChapter()
            );
        } else {
            result = jdbcTemplate.queryForObject(
                    "SELECT create_single_sub_view(?, ?)",
                    Boolean.class,
                    config.getViewName(), bookId
            );
        }

        if (Boolean.TRUE.equals(result)) {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT create_view_indexes(?)",
                    Boolean.class,
                    config.getViewName()
            ));
        }

        return false;
    }

    private boolean refreshSingleViewConcurrently(String viewName) {
        log.trace("Refreshing single view async: {}", viewName);

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT refresh_single_view_concurrently(?)",
                Boolean.class,
                viewName
        ));
    }

    private boolean refreshSingleView(String viewName) {
        log.trace("Refreshing single view: {}", viewName);

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT refresh_single_view(?)",
                Boolean.class,
                viewName
        ));
    }

    private boolean dropSingleView(String viewName) {
        log.trace("Dropping single view: {}", viewName);

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT drop_single_view(?)",
                Boolean.class,
                viewName
        ));
    }

    private boolean viewExists(String viewName) {
        log.trace("Checking if view exists: {}", viewName);

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT view_exists(?)",
                Boolean.class,
                viewName
        ));
    }

    private void createLinesInfoView(List<String> viewNames) {
        log.trace("Creating lines info view with {} views", viewNames.size());

        var viewArray = viewNames.toArray(new String[0]);
        var result = jdbcTemplate.queryForObject(
                "SELECT create_lines_info_view(?)",
                new Object[]{viewArray},
                Boolean.class
        );
        log.debug("Lines info view creation: {}", Boolean.TRUE.equals(result) ? "success" : "failed");
    }

    private String calculateHash(String bookId, BookSettings settings) {
        return md5(bookId + "|" + settings.toHashString());
    }

    private Integer getChapterCount(String bookId) {
        log.trace("Getting chapter count for bookId: {}", bookId);

        var result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(COUNT(*), 0) FROM chapter_info WHERE cast(book_id as text) = cast(? as text)",
                Long.class,
                bookId);

        return Math.toIntExact(result);
    }

    private List<ViewConfig> calculateViewConfigs(String bookId, BookSettings settings) {
        log.trace("Calculating view configs for bookId: {}, maxChaptersPerView: {}, minChaptersForSplit: {}",
                bookId, settings.getMaxChaptersPerView(), settings.getMinChaptersForSplit());

        int chapterCount = getChapterCount(bookId);
        var configs = new ArrayList<ViewConfig>();

        if (chapterCount < settings.getMinChaptersForSplit()) {
            configs.add(ViewConfig.builder()
                    .viewName("lines_info_" + settings.getTemplateName())
                    .build());
        } else {
            int numViews = (int) Math.ceil((double) chapterCount / settings.getMaxChaptersPerView());
            for (int i = 1; i <= numViews; i++) {
                int startChapter = (i - 1) * settings.getMaxChaptersPerView() + 1;
                int endChapter = Math.min(i * settings.getMaxChaptersPerView(), chapterCount);

                configs.add(ViewConfig.builder()
                        .viewName("lines_info_%s_part_%d".formatted(settings.getTemplateName(), i))
                        .startChapter(startChapter)
                        .endChapter(endChapter)
                        .build());
            }
        }

        return configs;
    }

    private String parseViewName(String bookId, Integer chapterNumber, BookSettings settings) {
        // Find which view contains the chapter
        for (var viewName : settings.getViewNames()) {
            // Extract chapter range from view name
            if (viewName.contains("lines_info_%s_part_".formatted(settings.getTemplateName()))) {
                // Parse part number to determine chapter range
                int startIndex = viewName.lastIndexOf("_part_") + 6;
                int endIndex = viewName.length();
                var partNumberStr = viewName.substring(startIndex, endIndex);

                try {
                    int partNumber = Integer.parseInt(partNumberStr);
                    int startChapter = (partNumber - 1) * settings.getMaxChaptersPerView() + 1;
                    int endChapter = Math.min(partNumber * settings.getMaxChaptersPerView(),
                            getChapterCount(bookId));

                    if (chapterNumber >= startChapter && chapterNumber <= endChapter) {
                        return viewName;
                    }
                } catch (NumberFormatException e) {
                    // Continue to next view
                }
            } else if (viewName.equals("lines_info_" + settings.getTemplateName())) {
                // Single view for small books
                return viewName;
            }
        }

        return null;
    }

    private String escapeObjectName(String title) {
        if (title == null) {
            return null;
        }

        if (title.length() > 36) {
            return escapeObjectName(md5(title));
        }

        return title.toLowerCase().replaceAll("[^a-z0-9_]", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ViewConfig {

        private String viewName;
        private Integer startChapter;
        private Integer endChapter;

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class BookSettings {

        private Integer maxChaptersPerView;
        private Integer minChaptersForSplit;
        private String templateName;
        private List<String> viewNames;

        public String toHashString() {
            StringBuilder sb = new StringBuilder();
            if (maxChaptersPerView != null) {
                sb.append("maxChaptersPerView=").append(maxChaptersPerView).append("|");
            }
            if (minChaptersForSplit != null) {
                sb.append("minChaptersForSplit=").append(minChaptersForSplit).append("|");
            }
            if (templateName != null) {
                sb.append("templateName=").append(templateName).append("|");
            }
            if (viewNames != null) {
                List<String> sortedViewNames = new ArrayList<>(viewNames);
                Collections.sort(sortedViewNames);
                sb.append("viewNames=").append(String.join(",", sortedViewNames)).append("|");
            }

            return md5(sb.toString());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Initializer {

        private final ExecutorService executorService = Executors.newFixedThreadPool(
                Math.max(Runtime.getRuntime().availableProcessors(), 4));

        private final LinesInfoDao linesInfoService;
        private final BookService bookService;

        @SneakyThrows
        @PostConstruct
        public void init() {
            log.trace("Prepare to refresh lines_info view...");
            CompletableFuture.runAsync(() -> {
                var books = bookService.findAll();
                var ids = books.stream()
                        .map(Book::getId)
                        .toList();

                log.trace("Prepare to create book settings...");
                var settings = ids.stream().map(this::findOrCreateSettingsAsync)
                        .toList();
                settings.forEach(checked(CompletableFuture::get));

                log.trace("Prepare to create mat views...");
                var views = ids.stream().map(this::processBookViewsAsync)
                        .toList();

                int result = views.stream()
                        .map(CheckedFunction.checked(CompletableFuture::get))
                        .mapToInt(value -> value)
                        .sum();

                if (result > 0) {
                    log.trace("Recreate view...");
                    recreateLinesInfoViewAsync(ids);
                }
            }, executorService);
        }


        public CompletableFuture<Void> findOrCreateSettingsAsync(String bookId) {
            return CompletableFuture.runAsync(() -> linesInfoService.findOrCreateSettings(bookId), executorService)
                    .exceptionally(ex -> {
                        log.error("ERROR: ", ex);

                        return null;
                    });
        }

        public CompletableFuture<Integer> processBookViewsAsync(String bookId) {
            return CompletableFuture.supplyAsync(() -> linesInfoService.createMatViews(bookId), executorService)
                    .exceptionally(ex -> {
                        log.error("ERROR: ", ex);

                        return null;
                    });
        }

        public CompletableFuture<Void> recreateLinesInfoViewAsync(List<String> bookIds) {
            return CompletableFuture.runAsync(() -> linesInfoService.recreateLinesInfoView(bookIds), executorService)
                    .exceptionally(ex -> {
                        log.error("ERROR: ", ex);

                        return null;
                    });
        }

    }

}