package machinum.extract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Chapter;
import machinum.model.NameSimilarity;
import machinum.model.ObjectName;
import machinum.processor.core.*;
import machinum.tool.RawInfoTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NameRelationAnalyzer implements ChunkSupport, FlowSupport, PreconditionSupport {

    public static final int POSSIBLE_RESPONSE_SIZE = 2_000;

    @Value("${app.name-relations.temperature}")
    protected final Double temperature;
    @Value("${app.name-relations.numCtx}")
    protected final Integer contextLength;
    @Getter
    @Value("${app.name-relations.model}")
    private final String chatModel;
    @Value("classpath:prompts/custom/system/NameRelationsSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/NameRelations.ST")
    private final Resource analyzerTemplate;

    private final Assistant assistant;
    private final RawInfoTool rawInfoTool;
    private final RetryHelper retryHelper;
    private final Worker worker;
    private final ObjectMapper objectMapper;

    public List<NameSimilarity> analyzeRelations(GlossarySimilarityRecord record) {
        log.debug("Analyzing relations for name: {}", record.getObjectName().getName());

        try {
            var history = worker.createSystemHistory(null, systemTemplate);
            var numCtx = worker.getSlidingWindow(1000, history, contextLength); // Conservative token estimate

            var context = AssistantContext.builder()
                    .flowContext(null) // No flow context needed
                    .actionResource(analyzerTemplate)
                    .history(history)
                    .inputs(prepareInputs(record))
                    .tools(List.of(rawInfoTool))
                    .customizeChatOptions(options -> {
                        options.setNumCtx(numCtx);
                        options.setModel(getChatModel());
                        options.setTemperature(temperature);
                        return options;
                    })
                    .text("") // No main text
                    .build();

            var contextResult = worker.work(context, "analyzeNameRelations", result -> {
                String parsed = requireNotEmpty(parseResult(result));
                result.replaceResult(parsed);
                return result;
            }, Worker.RetryType.SMALL);

            String result = contextResult.result();
            return parseNameSimilarities(result);

        } catch (Exception e) {
            log.warn("Failed to analyze relations for '{}': {}", record.getObjectName().getName(), e.getMessage());
            return record.getInitialSimilarities(); // Fallback to initial similarities
        }
    }

    private Map<String, String> prepareInputs(GlossarySimilarityRecord record) {
        var objectName = record.getObjectName();

        return Map.of(
                "currentName", objectName.getName(),
                "currentDescription", objectName.getDescription() != null ? objectName.getDescription() : "",
                "initialSimilarNames", formatNameList(record.getInitialSimilarities()),
                "contextualSimilarNames", findContextualSimilarNames(record),
                "additionalDescriptions", collectAdditionalDescriptions(record)
        );
    }

    private String formatNameList(List<NameSimilarity> similarities) {
        return similarities.stream()
                .map(sim -> String.format("- %s (confidence: %.2f)", sim.getName(), sim.getConfidence()))
                .collect(Collectors.joining("\n"));
    }

    private String findContextualSimilarNames(GlossarySimilarityRecord record) {
        // TODO: Implement embedding search for similar records
        // This should search for names/chapters with similar embeddings

        if (1 < 2) {
            throw new IllegalStateException("Not implemented yet");
        }

        return "No contextual similarities found yet"; // Placeholder
    }

    private String collectAdditionalDescriptions(GlossarySimilarityRecord record) {
        // TODO: Collect descriptions from similar records/contexts
        var descriptions = new StringBuilder();
        if (record.getObjectName().getDescription() != null) {
            descriptions.append(record.getObjectName().getDescription());
        }
        descriptions.append("\nContext from chapter ").append(record.getChapter().getNumber());
        return descriptions.toString();
    }

    private String parseResult(AssistantContext.Result contextResult) {
        return contextResult.result().trim();
    }

    private List<NameSimilarity> parseNameSimilarities(String jsonResult) {
        try {
            // Parse JSON array of NameSimilarity objects
            List<NameSimilarity> similarities = objectMapper.readValue(
                    jsonResult,
                    new TypeReference<List<NameSimilarity>>() {
                    }
            );

            // Set lastUpdated if not provided
            similarities.forEach(sim -> {
                if (sim.getLastUpdated() == null) {
                    sim.setLastUpdated(LocalDateTime.now());
                }
            });

            return similarities;
        } catch (Exception e) {
            log.warn("Failed to parse name similarities from LLM result: {} - Result was: {}",
                    e.getMessage(), jsonResult);
            return List.of();
        }
    }

    @Data
    public static class GlossarySimilarityRecord {
        private ObjectName objectName;
        private List<NameSimilarity> initialSimilarities;
        private Chapter chapter;
        private String bookId;
    }

}
