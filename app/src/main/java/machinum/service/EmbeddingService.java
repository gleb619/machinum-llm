package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.ChapterController.GlossarySearchRequest;
import machinum.converter.ChapterContextMapper;
import machinum.converter.NamesContextMapper;
import machinum.extract.NameRelationAnalyzer;
import machinum.extract.NameRelationAnalyzer.GlossarySimilarityRecord;
import machinum.extract.Splitter;
import machinum.flow.model.FlowContext;
import machinum.model.*;
import machinum.repository.ChapterContextRepository.ChapterSimilarityProjection;
import machinum.repository.ChapterGlossaryRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static machinum.service.ChapterGlossaryService.generateTrigrams;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    public static final String TITLE = "title";
    public static final String TRANSLATED_TITLE = "translated_title";
    public static final String TEXT = "text";
    public static final String TRANSLATED_TEXT = "translated_text";
    public static final String SUMMARY = "summary";

    private final EmbeddingModel embeddingModel;
    private final NamesContextService namesContextService;
    private final ChapterContextService chapterContextService;
    private final ChapterGlossaryService chapterGlossaryService;
    private final ChapterGlossaryRepository chapterRepository;
    private final ChapterContextMapper chapterContextMapper;
    private final NamesContextMapper namesContextMapper;
    private final ObjectMapper objectMapper;
    private final Splitter splitter;
    private final NameRelationAnalyzer nameRelationAnalyzer;

    /**
     * Process embeddings for a single chapter - generates embeddings for specified chapter fields
     * and all names from the chapter glossary based on the execution type.
     *
     * @param context Flow context containing the chapter
     * @param type    Type of execution: ALL, ONLY_CHAPTER, ONLY_NAMES, or NONE
     * @return Updated flow context
     */
    @Transactional
    public FlowContext<Chapter> processChapterEmbeddings(FlowContext<Chapter> context, EmbeddingExecutionType type) {
        var chapter = context.getCurrentItem();
        log.info("ENTERING processChapterEmbeddings for chapter: {} with type: {}", chapter.getNumber(), type);

        // Delete existing embeddings based on type to allow regeneration
        if (type == EmbeddingExecutionType.ALL || type == EmbeddingExecutionType.ONLY_CHAPTER) {
            chapterContextService.deleteById(chapter.getId());
        }
        if (type == EmbeddingExecutionType.ALL || type == EmbeddingExecutionType.ONLY_NAMES) {
            namesContextService.deleteAllNamesContextsByChapterId(chapter.getId());
        }

        try {
            switch (type) {
                case ALL -> {
                    log.info("ALL case: calling generateChapterFieldEmbeddings");
                    generateChapterFieldEmbeddings(chapter);
                    log.info("ALL case: calling generateNamesEmbeddings");
                    generateNamesEmbeddings(chapter);
                }
                case ONLY_CHAPTER -> {
                    log.info("ONLY_CHAPTER case: calling generateChapterFieldEmbeddings");
                    generateChapterFieldEmbeddings(chapter);
                }
                case ONLY_NAMES -> {
                    log.info("ONLY_NAMES case: calling generateNamesEmbeddings");
                    generateNamesEmbeddings(chapter);
                }
                case NONE -> log.info("Skipping embedding generation for chapter {}", chapter.getNumber());
            }

            log.info("Successfully processed embeddings for chapter: {}", chapter.getNumber());
        } catch (Exception e) {
            log.error("Failed to process embeddings for chapter: {} with exception: {}", chapter.getNumber(), e.getMessage());
            throw new RuntimeException("Embedding generation failed for chapter " + chapter.getNumber(), e);
        }

        return context;
    }

    /**
     * Perform glossary consolidation for a chapter - find similar names and populate ObjectName similarNames.
     * This method identifies unique names in the current chapter and searches for similar names using
     * both database search functions and embedding-based similarity.
     *
     * @param context FlowContext containing the current chapter being processed
     * @return FlowContext with updated chapter containing enriched similar names
     */
    public FlowContext<Chapter> consolidateGlossary(FlowContext<Chapter> context) {
        var chapter = context.getCurrentItem();
        var bookId = chapter.getBookId();
        log.info("Performing semantic glossary consolidation for chapter: {}", chapter.getNumber());

        try {
            var chapterNames = context.getCurrentItem().getNames();

            if (chapterNames == null || chapterNames.isEmpty()) {
                log.info("No names found in chapter {}", chapter.getNumber());
                return context;
            }

            // Process each name to find initial similar names
            var records = chapterNames.stream()
                    .map(objectName -> findSimilarNamesForGlossaryItem(bookId, chapter, objectName))
                    .collect(Collectors.toList());

            // Use LLM analyzer to refine similar names
            var updatedNames = records.stream()
                    .map(record -> {
                        var llmSimilarities = nameRelationAnalyzer.analyzeRelations(record);
                        var combined = new ArrayList<>(record.getInitialSimilarities());
                        combined.addAll(llmSimilarities);

                        // De-duplicate by name, keeping highest confidence
                        var finalSimilarities = combined.stream()
                                .collect(Collectors.groupingBy(NameSimilarity::getName))
                                .values().stream()
                                .map(list -> list.stream().max(Comparator.comparing(NameSimilarity::getConfidence)).orElseThrow())
                                .sorted(Comparator.comparing(NameSimilarity::getConfidence).reversed())
                                .limit(5)
                                .collect(Collectors.toList());

                        var updatedName = record.getObjectName().toBuilder()
                                .metadata(new HashMap<>(record.getObjectName().getMetadata()))
                                .build();
                        updatedName.similarNames(finalSimilarities);

                        return updatedName;
                    })
                    .collect(Collectors.toList());

            // Update the chapter with enriched names
            var updatedChapter = chapter.toBuilder()
                    .names(updatedNames)
                    .build();

            // Log statistics
            var totalSimilarities = updatedNames.stream()
                    .mapToInt(name -> name.similarNames().size())
                    .sum();

            log.info("Found {} names with {} total semantic similarities in chapter {}",
                    updatedNames.size(), totalSimilarities, chapter.getNumber());

            //TODO update context
            return context.withCurrentItem(updatedChapter);
        } catch (Exception e) {
            log.error("Failed to perform glossary consolidation for chapter {}: {}", chapter.getNumber(), e.getMessage());
            return context;
        }
    }

    /**
     * Generate embeddings for specified chapter fields.
     * Creates one ChapterContextEntity record per chapter with all field embeddings.
     */
    private void generateChapterFieldEmbeddings(Chapter chapter) {
        log.info("ENTERING generateChapterFieldEmbeddings for chapter: {}", chapter.getNumber());
        // Build the ChapterContextEntity with all field contents and embeddings
        // Initialize JSONB fields with empty arrays to avoid null insertion issues
        var builder = ChapterContext.builder()
                .id(chapter.getId())
                .bookId(chapter.getBookId())
                .chapterNumber(chapter.getNumber())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .textContent("[]")           // Default empty JSON array for failed text processing
                .textEmbedding("[]")         // Default empty JSON array for failed embedding
                .translatedTextContent("[]") // Default empty JSON array for failed text processing
                .translatedTextEmbedding("[]"); // Default empty JSON array for failed embedding

        // Generate embeddings for each field
        if (chapter.getTitle() != null && !chapter.getTitle().trim().isEmpty()) {
            try {
                builder.titleContent(chapter.getTitle())
                        .titleEmbedding(embeddingModel.embed(chapter.getTitle()));
            } catch (Exception e) {
                log.warn("Failed to generate embedding for title in chapter {}: {}",
                        chapter.getNumber(), e.getMessage());
            }
        }

        if (chapter.getTranslatedTitle() != null && !chapter.getTranslatedTitle().trim().isEmpty()) {
            try {
                builder.translatedTitleContent(chapter.getTranslatedTitle())
                        .translatedTitleEmbedding(embeddingModel.embed(chapter.getTranslatedTitle()));
            } catch (Exception e) {
                log.warn("Failed to generate embedding for translatedTitle in chapter {}: {}",
                        chapter.getNumber(), e.getMessage());
            }
        }

        if (chapter.getText() != null && !chapter.getText().trim().isEmpty()) {
            try {
                var chunks = splitter.work(chapter.getText(), 1000); // Split text into chunks
                var chunkTexts = chunks.stream()
                        .map(chunk -> chunk.getText())
                        .collect(Collectors.toList());
                var chunkEmbeddings = chunks.stream()
                        .map(chunk -> embeddingModel.embed(chunk.getText()))
                        .collect(Collectors.toList());
                builder.textContent(objectMapper.writeValueAsString(chunkTexts))
                        .textEmbedding(objectMapper.writeValueAsString(chunkEmbeddings));
            } catch (Exception e) {
                log.warn("Failed to generate embedding for text in chapter {}: {}",
                        chapter.getNumber(), e.getMessage());
            }
        }

        if (chapter.getTranslatedText() != null && !chapter.getTranslatedText().trim().isEmpty()) {
            try {
                var chunks = splitter.work(chapter.getTranslatedText(), 1000); // Split translated text into chunks
                var chunkTexts = chunks.stream()
                        .map(chunk -> chunk.getText())
                        .collect(Collectors.toList());
                var chunkEmbeddings = chunks.stream()
                        .map(chunk -> embeddingModel.embed(chunk.getText()))
                        .collect(Collectors.toList());
                builder.translatedTextContent(objectMapper.writeValueAsString(chunkTexts))
                        .translatedTextEmbedding(objectMapper.writeValueAsString(chunkEmbeddings));
            } catch (Exception e) {
                log.warn("Failed to generate embedding for translatedText in chapter {}: {}",
                        chapter.getNumber(), e.getMessage());
            }
        }

        if (chapter.getSummary() != null && !chapter.getSummary().trim().isEmpty()) {
            try {
                builder.summaryContent(chapter.getSummary())
                        .summaryEmbedding(embeddingModel.embed(chapter.getSummary()));
            } catch (Exception e) {
                log.warn("Failed to generate embedding for summary in chapter {}: {}",
                        chapter.getNumber(), e.getMessage());
            }
        }

        // Save the single ChapterContextEntity record
        var entity = builder.build();
        log.info("About to save ChapterContext entity: id={}, bookId={}, titleContent length={}",
                entity.getId(), entity.getBookId(), entity.getTitleContent() != null ? entity.getTitleContent().length() : 0);
        chapterContextService.save(entity);
        log.info("Saved chapter context embeddings for chapter {}", chapter.getNumber());
    }

    /**
     * Generate embeddings for all names from the chapter glossary.
     */
    private void generateNamesEmbeddings(Chapter chapter) {
        var namesContextEntities = new ArrayList<NamesContext>();

        if (chapter.getNames() != null) {
            int counter = 1; // Start sequential numbering
            for (var name : chapter.getNames()) {
                if (name.getName() != null && !name.getName().trim().isEmpty()) {
                    try {
                        var embedding = embeddingModel.embed(name.stringValue());
                        var id = chapter.getId() + counter; // chapterId + sequential number
                        var entity = NamesContext.builder()
                                .id(id)
                                .chapterId(chapter.getId())
                                .name(name.getName())
                                .embedding(embedding)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        namesContextEntities.add(entity);
                        counter++;
                    } catch (Exception e) {
                        log.warn("Failed to generate embedding for name '{}' in chapter {}: {}",
                                name.getName(), chapter.getNumber(), e.getMessage());
                    }
                }
            }
        }

        if (!namesContextEntities.isEmpty()) {
            namesContextService.saveAllNamesContexts(namesContextEntities);
            log.debug("Saved {} names context embeddings for chapter {}",
                    namesContextEntities.size(), chapter.getNumber());
        }
    }

    /**
     * Search for chapters similar to the given text content within a specific field.
     *
     * @param content   The text content to search for
     * @param fieldType The field to search in (title, text, summary, etc.)
     * @param threshold Minimum similarity threshold (0.0-1.0)
     * @param limit     Maximum number of results
     * @return List of similarity results sorted by relevance
     */
    public List<ChapterSimilarityResult> findSimilarChaptersByField(String bookId, String content, String fieldType, double threshold, int limit) {
        log.debug("Searching for chapters similar to '{}' in field '{}' (threshold: {}, limit: {})",
                truncateContent(content), fieldType, threshold, limit);

        try {
            var embedding = embeddingModel.embed(content);
            var embeddingStr = Arrays.toString(embedding);
            var results = chapterContextService.findSimilarByFieldProjected(bookId, embeddingStr, fieldType, threshold, limit);

            return results.stream()
                    .map(this::convertToChapterSimilarityResult)
                    .filter(result -> result.getSimilarity() >= threshold)
                    .sorted(Comparator.comparing(ChapterSimilarityResult::getSimilarity).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to search chapters by field: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Search for chapters similar to the given text content across all fields.
     *
     * @param content   The text content to search for
     * @param threshold Minimum similarity threshold (0.0-1.0)
     * @param limit     Maximum number of results
     * @return List of similarity results sorted by relevance
     */
    public List<ChapterSimilarityResult> findSimilarChaptersAcrossFields(String bookId, String content, double threshold, int limit) {
        log.debug("Searching for chapters similar to '{}' across all fields (threshold: {}, limit: {})",
                truncateContent(content), threshold, limit);

        try {
            var embedding = embeddingModel.embed(content);
            var embeddingStr = Arrays.toString(embedding);
            var results = chapterContextService.findSimilarAcrossFieldsProjected(bookId, embeddingStr, threshold, limit);

            return results.stream()
                    .map(this::convertToChapterSimilarityResult_Deprecated)
                    .filter(result -> result.getSimilarity() >= threshold)
                    .sorted(Comparator.comparing(ChapterSimilarityResult::getSimilarity).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to search chapters across fields: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private ChapterSimilarityResult convertToChapterSimilarityResult(ChapterSimilarityProjection projection) {
        var entity = projection.getEntity();
        var chapterContext = chapterContextMapper.toDto(entity);

        // Use projection data directly
        double distance = projection.getDistance();
        String matchedField = projection.getMatchedField();
        String matchedContent = switch (matchedField) {
            case TITLE -> entity.getTitleContent();
            case TRANSLATED_TITLE -> entity.getTranslatedTitleContent();
            case TEXT -> entity.getTextContent();
            case TRANSLATED_TEXT -> entity.getTranslatedTextContent();
            case SUMMARY -> entity.getSummaryContent();
            default -> null;
        };

        double similarity = Math.max(0.0, 1.0 - distance);

        return ChapterSimilarityResult.builder()
                .distance(distance)
                .similarity(similarity)
                .chapterContext(chapterContext)
                .matchedField(matchedField)
                .matchedContent(matchedContent)
                .build();
    }

    //TODO: remove duplicate code
    @Deprecated(forRemoval = true)
    private ChapterSimilarityResult convertToChapterSimilarityResult_Deprecated(ChapterSimilarityProjection projection) {
        var entity = projection.getEntity();
        var chapterContext = chapterContextMapper.toDto(entity);

        // Use projection data directly
        double distance = projection.getDistance();
        String matchedField = projection.getMatchedField();
        String matchedContent = switch (matchedField) {
            case TITLE -> entity.getTitleContent();
            case TRANSLATED_TITLE -> entity.getTranslatedTitleContent();
            case TEXT -> entity.getTextContent();
            case TRANSLATED_TEXT -> entity.getTranslatedTextContent();
            case SUMMARY -> entity.getSummaryContent();
            default -> null;
        };

        double similarity = Math.max(0.0, 1.0 - distance);

        return ChapterSimilarityResult.builder()
                .distance(distance)
                .similarity(similarity)
                .chapterContext(chapterContext)
                .matchedField(matchedField)
                .matchedContent(matchedContent)
                .build();
    }

    /**
     * Search for names similar to the given name text.
     *
     * @param name      The name to search for
     * @param threshold Minimum similarity threshold (0.0-1.0)
     * @param limit     Maximum number of results
     * @return List of similarity results sorted by relevance
     */
    public List<NameSimilarityResult> findByEmbeddingSimilarNames(String bookId, String name, double threshold, int limit) {
        log.debug("Searching for names similar to '{}' (threshold: {}, limit: {})",
                name, threshold, limit);

        try {
            var embedding = embeddingModel.embed(name);
            return chapterGlossaryService.findSimilarContextGlossaryNames(bookId, embedding, threshold, limit);
        } catch (Exception e) {
            log.error("Failed to search similar names: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find similar names for a single glossary item.
     * Checks if the name is unique in previous chapters and searches for similar names if it is.
     * Returns a record with initial similarities found from database and embedding searches.
     */
    private GlossarySimilarityRecord findSimilarNamesForGlossaryItem(String bookId, Chapter chapter, ObjectName objectName) {
        try {
            // Check if this name appears in previous chapters (uniqueness check)
            var existingNames = chapterRepository.findGlossaryByQuery(
                    List.of(objectName.getName()),
                    chapter.getNumber(),
                    bookId,
                    PageRequest.of(0, 100)
            );

            boolean isUnique = existingNames.isEmpty() ||
                    existingNames.stream().noneMatch(result ->
                            Objects.equals(result.getName(), objectName.getName()));

            if (!isUnique) {
                log.debug("Name '{}' is not unique in previous chapters, skipping similarity search", objectName.getName());
                return new GlossarySimilarityRecord() {{
                    setObjectName(objectName);
                    setInitialSimilarities(List.of());
                    setChapter(chapter);
                    setBookId(bookId);
                }};
            }

            // Search for similar names using multiple methods
            var similarNames = new ArrayList<NameSimilarity>();

            // 1. Database search functions
            similarNames.addAll(searchSimilarNamesInGlossary(bookId, chapter, objectName));

            // 2. Embedding-based search
            similarNames.addAll(searchSimilarNamesInEmbedding(bookId, chapter, objectName));

            // Remove duplicates and limit to top 5
            var uniqueSimilarNames = similarNames.stream()
                    .distinct()
                    .sorted(Comparator.comparing(NameSimilarity::getConfidence).reversed())
                    //TODO: before cut top 5 put data into LLM, create prompt, send, then parse result
                    .limit(5)
                    .collect(Collectors.toList());

            log.debug("Found {} initial similar names for '{}': {}",
                    uniqueSimilarNames.size(),
                    objectName.getName(),
                    uniqueSimilarNames.stream()
                            .map(sim -> String.format("%s (%.2f)", sim.getName(), sim.getConfidence()))
                            .collect(Collectors.joining(", ")));

            var record = new GlossarySimilarityRecord();
            record.setObjectName(objectName);
            record.setInitialSimilarities(uniqueSimilarNames);
            record.setChapter(chapter);
            record.setBookId(bookId);
            return record;

        } catch (Exception e) {
            log.warn("Failed to find similar names for '{}': {}", objectName.getName(), e.getMessage());
            var record = new GlossarySimilarityRecord();
            record.setObjectName(objectName);
            record.setInitialSimilarities(List.of());
            record.setChapter(chapter);
            record.setBookId(bookId);
            return record;
        }
    }

    /**
     * Search for similar names using database search functions (search_glossary, fuzzy_search_glossary).
     */
    private List<NameSimilarity> searchSimilarNamesInGlossary(String bookId, Chapter chapter, ObjectName objectName) {
        var similarNames = new ArrayList<NameSimilarity>();

        try {
            // Regular search
            var searchRequest = new GlossarySearchRequest();
            searchRequest.setSearchText(objectName.getName());
            searchRequest.setChapterStart(1);
            searchRequest.setChapterEnd(chapter.getNumber() - 1);
            searchRequest.setTopK(5);
            searchRequest.setMinScore(0.6f);

            var regularResults = chapterGlossaryService.searchGlossary(bookId, searchRequest);
            for (var result : regularResults) {
                var confidence = 0.7; // Default confidence for database search results

                similarNames.add(NameSimilarity.builder()
                        .name(result.getObjectName().getName())
                        .chapterId(result.getChapterId())
                        .chapterNumber(result.getChapterNumber())
                        .confidence(confidence)
                        .trustLevel(NameSimilarity.TrustLevel.CONFIRMED)
                        .reason("Database search match")
                        .lastUpdated(LocalDateTime.now())
                        .build());
            }

            // Fuzzy search using trigrams
            var fuzzyText = String.format("{\"query\": \"%s\", \"lengthRange\": {\"min\": %d, \"max\": %d}, \"nGrams\": %s}",
                    objectName.getName(),
                    Math.max(1, (int) Math.floor(objectName.getName().length() * 0.7)),
                    (int) Math.ceil(objectName.getName().length() * 1.3),
                    generateTrigrams(objectName.getName()));

            var fuzzyRequest = new GlossarySearchRequest();
            fuzzyRequest.setFuzzyText(fuzzyText);
            fuzzyRequest.setChapterStart(1);
            fuzzyRequest.setChapterEnd(chapter.getNumber() - 1);
            fuzzyRequest.setTopK(3);
            fuzzyRequest.setMinScore(0.4f);

            var fuzzyResults = chapterGlossaryService.searchGlossaryFuzzy(bookId, fuzzyRequest);
            for (var result : fuzzyResults) {
                var confidence = 0.5; // Default confidence for fuzzy database search results

                similarNames.add(NameSimilarity.builder()
                        .name(result.getObjectName().getName())
                        .chapterId(result.getChapterId())
                        .chapterNumber(result.getChapterNumber())
                        .confidence(confidence)
                        .trustLevel(NameSimilarity.TrustLevel.SUGGESTED)
                        .reason("Fuzzy database search match")
                        .lastUpdated(LocalDateTime.now())
                        .build());
            }

        } catch (Exception e) {
            log.warn("Failed to search similar names using database functions for '{}': {}",
                    objectName.getName(), e.getMessage());
        }

        return similarNames;
    }

    /**
     * Search for similar names using embedding-based similarity.
     */
    private List<NameSimilarity> searchSimilarNamesInEmbedding(String bookId, Chapter chapter, ObjectName objectName) {
        try {
            var results = findByEmbeddingSimilarNames(bookId, objectName.getName(), 0.7, 10);

            return results.stream()
                    .filter(result -> result.getSimilarity() >= 0.7)
                    .map(result -> NameSimilarity.builder()
                            .name(result.getObjectName().getName())
                            .chapterId(chapter.getId())
                            .chapterNumber(0)
                            .confidence(result.getSimilarity())
                            .trustLevel(NameSimilarity.TrustLevel.CONFIRMED)
                            .reason("Embedding similarity match")
                            .lastUpdated(LocalDateTime.now())
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Failed to search similar names using embeddings for '{}': {}", objectName.getName(), e.getMessage());
            return List.of();
        }
    }

    private String truncateContent(String content) {
        return content.length() > 50 ? content.substring(0, 47) + "..." : content;
    }

    public enum EmbeddingExecutionType {
        ALL,
        ONLY_CHAPTER,
        ONLY_NAMES,
        NONE
    }

}
