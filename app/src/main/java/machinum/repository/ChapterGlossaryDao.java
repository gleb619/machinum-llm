package machinum.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.controller.ChapterController.GlossarySearchRequest;
import machinum.model.ObjectName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Data Access Object for glossary search using functional algorithms.
 * Provides composable search functionality with embedded algorithm logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterGlossaryDao {

    private final JdbcTemplate jdbcTemplate;
    private final Holder<ObjectMapper> objectMapperHolder;

    // Functional algorithm registry: algorithm name -> (request, bookId) -> results
    private Map<String, BiFunction<GlossarySearchRequest, String, List<GlossarySearchResult>>> algorithms;

    @PostConstruct
    public void initializeAlgorithmRegistry() {
        algorithms = Map.of(
                "exact", (request, bookId) -> executeAlgorithm("find_glossary_exact_matches", request, bookId),
                "contains", (request, bookId) -> executeAlgorithm("find_glossary_contains_matches", request, bookId),
                "fulltext", (request, bookId) -> executeAlgorithm("find_glossary_fulltext_matches", request, bookId),
                "trigram", (request, bookId) -> executeAlgorithm("find_glossary_trigram_matches", request, bookId),
                "levenshtein", (request, bookId) -> executeAlgorithm("find_glossary_levenshtein_matches", request, bookId),
                "phonetic", (request, bookId) -> executeAlgorithm("find_glossary_phonetic_matches", request, bookId),
                "jaro_winkler", (request, bookId) -> executeAlgorithm("find_glossary_jaro_winkler_matches", request, bookId),
                "fuzzy", (request, bookId) -> executeAlgorithm("find_glossary_fuzzy_matches", request, bookId)
        );
    }

    /**
     * Search glossary using dynamically enabled algorithms.
     * Results are combined, deduplicated, and sorted by score.
     *
     * @param bookId  the book ID to search in
     * @param request the search request with algorithm selection
     * @return list of search results as GlossarySearchResult objects
     */
    public List<GlossarySearchResult> searchGlossary(@NonNull String bookId, @NonNull GlossarySearchRequest request) {
        log.debug("Searching glossary for bookId={}, searchText='{}'/'{}', algorithm='{}'",
                bookId, request.getSearchText(), request.getFuzzyText(), request.getAlgorithm());

        final String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "all";

        // Execute algorithms based on selection
        var allResults = algorithms.entrySet().stream()
                .filter(entry -> {
                    if ("all".equals(algorithm)) {
                        return true; // Run all algorithms
                    } else {
                        return algorithm.equals(entry.getKey()); // Run only specific algorithm
                    }
                })
                .flatMap(entry -> {
                    try {
                        var results = entry.getValue().apply(request, bookId);
                        if (!results.isEmpty()) {
                            log.trace("Algorithm {} found {} results", entry.getKey(), results.size());
                        }
                        return results.stream();
                    } catch (Exception e) {
                        log.error("Error executing algorithm {}", entry.getKey(), e);
                        return Stream.empty();
                    }
                })
                .toList();

        // Deduplicate by glossary_id (keep highest score for each unique term)
        Map<String, GlossarySearchResult> bestResults = allResults.stream()
                .collect(Collectors.toMap(
                        GlossarySearchResult::getGlossaryId, // deduplicate by glossary_id
                        result -> result, // takes the first one encountered
                        (existing, replacement) -> {
                            // Keep the one with higher score
                            return replacement.getScore() > existing.getScore() ? replacement : existing;
                        }
                ));

        // Convert back to list, sort by score descending, chapter ascending, then apply filtering
        List<GlossarySearchResult> sortedResults = bestResults.values().stream()
                .sorted(Comparator
                        .comparing(GlossarySearchResult::getScore, Comparator.reverseOrder())
                        .thenComparing(GlossarySearchResult::getChapterNumber)
                        .thenComparing(GlossarySearchResult::getName))
                .filter(result -> result.getScore() >= request.getMinScore())
                .limit(request.getTopK())
                .collect(Collectors.toList());

        log.debug("Combined search returned {} unique results after deduplication and filtering for '{}'/'{}'",
                sortedResults.size(), request.getSearchText(), request.getFuzzyText());

        return sortedResults;
    }

    /**
     * Execute a single algorithm with the given function name.
     */
    private List<GlossarySearchResult> executeAlgorithm(String functionName, GlossarySearchRequest request, String bookId) {
        String sql = String.format("SELECT * FROM %s('%s', ?, ?, ?)", functionName, bookId);
        List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sql,
                request.getSearchText(),
                request.getChapterStart(),
                request.getChapterEnd());

        return rawResults.stream()
                .map(this::parseSearchResult)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Parse a raw database row into a GlossarySearchResult
     */
    private GlossarySearchResult parseSearchResult(Map<String, Object> row) {
        ObjectName objectName = parseRawJson(row);
        if (objectName == null) {
            return null; // Skip rows where JSON parsing failed
        }
        return GlossarySearchResult.fromRawRow(row, objectName);
    }

    /**
     * Parse the raw_json column into an ObjectName object
     */
    private ObjectName parseRawJson(Map<String, Object> row) {
        String rawJson = (String) row.get("raw_json");
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class));
        } catch (Exception e) {
            // Log error and return null to filter out invalid entries
            return null;
        }
    }

    /**
     * Result of a glossary search operation containing both the parsed ObjectName
     * and metadata needed for scoring, sorting, and filtering.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GlossarySearchResult {

        private String glossaryId;
        private String chapterId;
        private String name;
        private String translatedName;
        private String category;
        private String description;
        private Integer chapterNumber;
        private String searchType;
        private Float score;
        private ObjectName objectName;

        /**
         * Create a GlossarySearchResult from a raw database row map
         */
        public static GlossarySearchResult fromRawRow(java.util.Map<String, Object> row, ObjectName objectName) {
            return new GlossarySearchResult(
                    (String) row.get("glossary_id"),
                    (String) row.get("chapter_id"),
                    (String) row.get("name"),
                    (String) row.get("translated_name"),
                    (String) row.get("category"),
                    (String) row.get("description"),
                    ((Number) row.get("chapter_number")).intValue(),
                    (String) row.get("search_type"),
                    ((Number) row.get("score")).floatValue(),
                    objectName
            );
        }

    }

}
