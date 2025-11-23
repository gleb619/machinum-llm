package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.controller.ChapterController.GlossarySearchRequest;
import machinum.converter.ChapterGlossaryMapper;
import machinum.converter.ChapterMapper;
import machinum.exception.AppIllegalStateException;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.model.ObjectName;
import machinum.repository.ChapterByGlossaryRepository;
import machinum.repository.ChapterGlossaryDao;
import machinum.repository.ChapterGlossaryRepository;
import machinum.repository.ChapterGlossaryRepository.CountResult;
import machinum.repository.ChapterGlossaryRepository.GlossaryByQueryResult;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static machinum.util.JavaUtil.uniqueBy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterGlossaryService {

    private final ChapterGlossaryRepository chapterRepository;
    private final ChapterByGlossaryRepository chapterByGlossaryRepository;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;
    private final ChapterGlossaryMapper chapterGlossaryMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterGlossaryDao chapterGlossaryDao;
    private final AsyncHelper asyncHelper;

    @Transactional(readOnly = true)
    public ChapterGlossary getById(@NonNull String bookId) {
        return findById(bookId)
                .orElseThrow(() -> new AppIllegalStateException("Glossary for given id=%s, is not found", bookId));
    }

    @Transactional(readOnly = true)
    public Optional<ChapterGlossary> findById(@NonNull String bookId) {
        return chapterRepository.findById(bookId)
                .map(chapterGlossaryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByGlossary(@NonNull List<String> names, @NonNull String bookId) {
        if (names.isEmpty()) {
            return Collections.emptyList();
        }

        var pairs = chapterByGlossaryRepository.findChaptersByGlossary(names, bookId);

        return pairs.stream()
                .map(chapterMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findGlossary(@NonNull Integer chapterNumber, @NonNull List<ObjectName> glossary, String bookId) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load references for glossary: {}", glossary.size());
        var names = uniqueBy(glossary, ObjectName::getName).stream()
                .map(ObjectName::getName)
                .collect(Collectors.toList());

        var pairs = chapterRepository.findGlossaryByQuery(names, chapterNumber, bookId, PageRequest.of(0, glossary.size() * 3));

        return pairs.stream()
                .map(GlossaryByQueryResult::getRawJson)
                .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findGlossaryWithAlternatives(@NonNull Integer chapterNumber,
                                                         @NonNull List<ObjectName> glossary, String bookId) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load references for glossary with alternatives: {}", glossary.size());
        var uniqueGlossary = uniqueBy(glossary, ObjectName::getName);
        var names = uniqueGlossary.stream()
                .map(ObjectName::getName)
                .collect(Collectors.toList());

        // Find exact matches first
        var exactMatches = chapterRepository.findGlossaryByQuery(names, chapterNumber, bookId, PageRequest.of(0, glossary.size() * 3))
                .stream()
                .map(GlossaryByQueryResult::getRawJson)
                .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                .toList();

        var exactNames = exactMatches.stream()
                .map(ObjectName::getName)
                .collect(Collectors.toSet());

        // Get remaining names without exact matches
        var remainingNames = names.stream()
                .filter(name -> !exactNames.contains(name))
                .toList();

        // Create suppliers for parallel execution
        var suppliers = remainingNames.stream()
                .map(remainingName -> (Supplier<List<ObjectName>>) () -> {
                    // Regular search request
                    var request = new GlossarySearchRequest();
                    request.setSearchText(remainingName);
                    request.setChapterStart(1);
                    request.setChapterEnd(chapterNumber);
                    request.setTopK(3);
                    request.setMinScore(0.1f);

                    var regularResults = chapterGlossaryDao.searchGlossary(bookId, request);

                    // Fuzzy search request
                    var fuzzyText = objectMapperHolder.execute(mapper -> mapper.writeValueAsString(Map.of(
                            "query", remainingName.toLowerCase(),
                            "lengthRange", Map.of(
                                    "min", Math.max(1, (int) Math.floor(remainingName.length() * 0.7)),
                                    "max", (int) Math.ceil(remainingName.length() * 1.3)
                            ),
                            "nGrams", generateTrigrams(remainingName)
                    )));
                    var fuzzyRequest = new GlossarySearchRequest();
                    fuzzyRequest.setFuzzyText(fuzzyText);
                    fuzzyRequest.setChapterStart(1);
                    fuzzyRequest.setChapterEnd(chapterNumber);
                    fuzzyRequest.setTopK(3);
                    fuzzyRequest.setMinScore(0.1f);

                    var fuzzyResults = chapterGlossaryDao.searchGlossary(bookId, fuzzyRequest);

                    // Combine results for this name
                    List<ObjectName> results = new ArrayList<>();
                    results.addAll(regularResults.stream()
                            .map(ChapterGlossaryDao.GlossarySearchResult::getObjectName)
                            .filter(objectName -> !exactNames.contains(objectName.getName()))
                            .toList());
                    results.addAll(fuzzyResults.stream()
                            .map(ChapterGlossaryDao.GlossarySearchResult::getObjectName)
                            .filter(objectName -> !exactNames.contains(objectName.getName()))
                            .toList());
                    return results;
                })
                .collect(Collectors.toList());

        // Flatten the results
        var alternatives = asyncHelper.executeAllInNewTransactions(suppliers, Runtime.getRuntime().availableProcessors() * 2).stream()
                .flatMap(List::stream)
                .toList();

        // Combine exact matches and alternatives, deduplicate preferring exact matches (put them first)
        var allResults = new ArrayList<>(exactMatches);
        allResults.addAll(alternatives);

        return uniqueBy(allResults, obj -> obj.getName() + "_" + obj.getCategory());
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findReferences(@NonNull Integer chapterNumber, @NonNull List<ObjectName> glossary, String bookId, int similarityRatio) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load references for glossary: {}", glossary.size());
        var names = uniqueBy(glossary, ObjectName::getName).stream()
                .map(ObjectName::getName)
                .collect(Collectors.toList());

        var pairs = chapterRepository.findGlossaryByQuery(names, chapterNumber, bookId, PageRequest.of(0, glossary.size() * 3));

        if (pairs.size() == names.size()) {
            return pairs.stream()
                    .map(GlossaryByQueryResult::getRawJson)
                    .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                    .collect(Collectors.toList());
        } else {
            var terms = findMissingNames(pairs, names);
            var termsOr = terms.stream()
                    .map(s -> String.join(" or ", s.split("\\s+")))
                    .collect(Collectors.toList());

            if (terms.isEmpty() || termsOr.isEmpty()) {
                return Collections.emptyList();
            }

            return chapterRepository.findLatestGlossaryListByQuery(terms, termsOr, chapterNumber, bookId).stream()
                    .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                    .collect(Collectors.toList());
//            return chapterRepository.findLatestGlossaryByQuery(chapterNumber, missingNames, bookId, similarityRatio).stream()
//                    .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
//                    .collect(Collectors.toList());
        }
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findTranslations(@NonNull String bookId, @NonNull List<ObjectName> glossary) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load translated names from old glossary terms: {}", glossary.size());
        return chapterRepository.findTranslatedNames(bookId, uniqueBy(glossary, ObjectName::getName).stream()
                        .map(ObjectName::getName)
                        .collect(Collectors.toList())).stream()
                .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookGlossary(@NonNull String bookId, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's glossary from db: {}", bookId);
        var result = chapterRepository.findGlossary(bookId, pageRequest)
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });

        return processUniqueState(bookId, result);
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookTranslatedGlossary(@NonNull String bookId, boolean translated, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's translated glossary from db: {}", bookId);
        var result = chapterRepository.findTranslatedGlossary(bookId, translated, pageRequest)
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });

        return processUniqueState(bookId, result);
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookTranslatedGlossary(@NonNull String bookId, Integer fromChapter, Integer toChapter, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's translated glossary from db: {}, range={}/{}", bookId, fromChapter, toChapter);
        var result = chapterRepository.findTranslatedGlossary(bookId, fromChapter, toChapter, pageRequest)
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });

        return processUniqueState(bookId, result);
    }

    @Transactional(readOnly = true)
    public List<ChapterGlossary> searchGlossary(@NonNull String bookId,
                                                GlossarySearchRequest request) {
        log.debug("Prepare to search glossary for: bookId={}, request={}", bookId, request);

        // Use fuzzy search if fuzzyText is provided
        if (request.getFuzzyText() != null && !request.getFuzzyText().isEmpty()) {
            return searchGlossaryFuzzy(bookId, request);
        }

        var result = chapterRepository.searchGlossary(bookId, request.getSearchText(), request.getChapterStart(),
                        request.getChapterEnd(), request.getTopK(), request.getMinScore()).stream()
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                })
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            // Fallback to new repository implementation
            return chapterGlossaryDao.searchGlossary(bookId, request).stream()
                    .map(searchResult -> {
                        var dto = new ChapterGlossary();
                        dto.setId(searchResult.getGlossaryId());
                        dto.setChapterId(searchResult.getChapterId());
                        dto.setChapterNumber(searchResult.getChapterNumber());
                        dto.setObjectName(searchResult.getObjectName());

                        return dto;
                    })
                    .collect(Collectors.toList());
        } else {
            return uniqueBy(result, ChapterGlossary::getId);
        }
    }

    @Transactional(readOnly = true)
    public List<ChapterGlossary> searchGlossaryFuzzy(@NonNull String bookId,
                                                     GlossarySearchRequest request) {
        log.debug("Prepare to search glossary fuzzy for: bookId={}, fuzzyRequest={}", bookId,
                objectMapperHolder.execute(mapper -> mapper.readValue(request.getFuzzyText(), Map.class)));

        var result = chapterRepository.searchGlossaryFuzzy(bookId, request.getFuzzyText(), request.getChapterStart(),
                        request.getChapterEnd(), request.getTopK(), request.getMinScore()).stream()
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                })
                .collect(Collectors.toList());

        return uniqueBy(result, ChapterGlossary::getId);
    }

    @Transactional
    public void replaceText(@NonNull String bookId, String search, String replacement) {
        log.debug("Replacing text in bookId: {}", bookId);
        chapterRepository.replaceText(bookId, search, replacement);
        log.debug("Text replacement completed for bookId: {}", bookId);
    }

    @Transactional
    public void replaceTextById(@NonNull String chapterId, String search, String replacement) {
        log.debug("Replacing text in chapterId: {}", chapterId);
        chapterRepository.replaceTextById(chapterId, search, replacement);
        log.debug("Text replacement completed for chapterId: {}", chapterId);
    }

    @Transactional
    public void replaceTextForColumn(@NonNull String chapterId, String columnName, String search, String replacement) {
        log.debug("Replacing text in column: {} for chapterId: {}", columnName, chapterId);
        chapterRepository.replaceTextForColumn(chapterId, columnName, search, replacement);
        log.debug("Text replacement completed for column: {} in chapterId: {}", columnName, chapterId);
    }

    @Transactional
    public void replaceSummary(@NonNull String bookId, String search, String replacement) {
        log.debug("Replacing summary in bookId: {}", bookId);
        chapterRepository.replaceSummary(bookId, search, replacement);
        log.debug("Summary replacement completed for bookId: {}", bookId);
    }

    @Transactional
    public List<String> updateGlossaryRuName(@NonNull String bookId, String oldRuName, String newRuName, Boolean returnIds, String nameFilter) {
        if (returnIds == null) {
            log.warn("returnIds is null for bookId: {}", bookId);
        }
        log.debug("Updating glossary ru name in bookId: {}", bookId);
        String result = chapterRepository.updateGlossaryRuName(bookId, oldRuName, newRuName, returnIds, nameFilter);
        log.debug("Glossary ru name update completed for bookId: {}", bookId);
        if (returnIds != null && returnIds) {
            try {
                return objectMapperHolder.execute(mapper -> mapper.readValue(result, List.class));
            } catch (Exception e) {
                log.error("Failed to parse ids: {}", result, e);
                return List.of();
            }
        } else {
            return null;
        }
    }

    /* ============= */

    private List<String> findMissingNames(List<GlossaryByQueryResult> pairs, List<String> dtoNames) {
        // Find Pair's first values that are not in dtoNames
        return pairs.stream()
                .map(GlossaryByQueryResult::getName)
                .filter(name -> !dtoNames.contains(name))
                .collect(Collectors.toList());
    }

    private Page<ChapterGlossary> processUniqueState(@NonNull String bookId, Page<ChapterGlossary> result) {
        var list = result.getContent();
        var lastGlossary = Optional.ofNullable(list.isEmpty() ? ChapterGlossary.builder().build() : list.getLast());
        var names = result.map(chapterGlossary -> chapterGlossary.getObjectName().getName()).getContent();

        var chapterNumber = lastGlossary.filter(v -> Objects.nonNull(v.getChapterNumber()))
                .map(ChapterGlossary::getChapterNumber)
                .orElse(1);
        var countResults = chapterRepository.countGlossaryInPreviousChapters(names, chapterNumber, bookId).stream()
                .collect(Collectors.toMap(CountResult::getName, CountResult::getCount));

        return result.map(chapterGlossary -> {
            var name = chapterGlossary.getObjectName().getName();
            chapterGlossary.setUnique(countResults.getOrDefault(name, 0L) <= 1L);
            return chapterGlossary;
        });
    }

    private List<String> generateTrigrams(String text) {
        // Clean the text: remove special characters, keep only letters, spaces, and numbers
        String cleanedText = text.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase();

        Set<String> nGrams = new HashSet<>();
        for (int i = 0; i <= cleanedText.length() - 3; i++) {
            String trigram = cleanedText.substring(i, i + 3);

            // Only add trigrams that consist only of letters or numbers (no spaces in middle)
            if (trigram.matches("^[a-zA-Z0-9]+$")) {
                nGrams.add(trigram);
            }
        }

        return new ArrayList<>(nGrams);
    }

}
