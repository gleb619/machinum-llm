package machinum.processor;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.AppFlowActions;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.processor.HistoryService.ContextBundle.ContextBundleBuilder;
import machinum.processor.core.FlowSupport.HistoryItem;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.countTokens;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final int MAX_TOKENS = 10_000;
    private static final int SYSTEM_TEMPLATE_RESERVE = 500;
    private static final int TARGET_RESPONSE_RESERVE = 1_000;
    private static final double COMPRESSION_THRESHOLD = 0.7;
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern DUPLICATE_PATTERN = Pattern.compile("\\b(\\w+(?:\\s+\\w+){0,3})\\b(?=.*\\b\\1\\b)", Pattern.CASE_INSENSITIVE);


    public List<Message> buildOptimizedContext(FlowContext<Chapter> flowContext,
                                               Resource systemTemplate,
                                               HistoryItem... allowedItems) {

        var availableTokens = calculateAvailableTokens(systemTemplate);
        var items = Arrays.asList(allowedItems.length == 0 ? HistoryItem.values() : allowedItems);

        var builder = ContextBundle.builder()
                .maxTokens(availableTokens)
                .systemMessage(systemTemplate);

        return buildContextWithPriority(flowContext, builder, items, availableTokens)
                .toMessages();
    }

    private ContextBundle buildContextWithPriority(FlowContext<Chapter> flowContext,
                                                   ContextBundleBuilder builder,
                                                   List<HistoryItem> items,
                                                   int availableTokens) {

        var tokenBudget = TokenBudget.from(availableTokens);

        // Priority 1: Essential glossary (most recent)
        if (items.contains(HistoryItem.GLOSSARY)) {
            addCurrentGlossary(flowContext, builder, tokenBudget);
        }

        // Priority 2: Recent context (compressed if needed)
        if (items.contains(HistoryItem.CONTEXT)) {
            addCurrentContext(flowContext, builder, tokenBudget);
        }

        return builder.build();
    }

    private void addCurrentGlossary(FlowContext<Chapter> flowContext,
                                    ContextBundleBuilder builder,
                                    TokenBudget budget) {

        flowContext.hasArgument(AppFlowActions::glossaryArg, glossary -> {
            var glossaryText = formatGlossary(glossary.getValue());
            int tokens = countTokens(glossaryText);

            if (budget.canAllocate(tokens)) {
                builder.entry(ContextEntry.glossary(glossaryText, tokens));
                budget.allocate(tokens);
            } else {
                log.error("We need to figure out how else to shrink the size of the glossary.");
                // Compress glossary by removing less important entries
//                var compressed = glossaryService.compressGlossary(glossary.getValue(), budget.remaining());
//                if (!compressed.isEmpty()) {
//                    int compressedTokens = countTokens(compressed);
//                    builder.entry(ContextEntry.glossary(compressed, compressedTokens));
//                    budget.allocate(compressedTokens);
//                }
            }
        });
    }

    private void addCurrentContext(FlowContext<Chapter> flowContext,
                                   ContextBundleBuilder builder,
                                   TokenBudget budget) {

        flowContext.hasArgument(FlowContext::contextArg, context -> {
            var contextText = context.stringValue();
            int tokens = countTokens(contextText);

            if (budget.canAllocate(tokens)) {
                builder.entry(ContextEntry.context(contextText, tokens));
                budget.allocate(tokens);
            } else {
                // Apply intelligent compression
                var compressed = intelligentCompress(contextText, budget.remaining());
                if (!compressed.isEmpty()) {
                    int compressedTokens = countTokens(compressed);
                    builder.entry(ContextEntry.context(compressed, compressedTokens));
                    budget.allocate(compressedTokens);
                }
            }
        });
    }

    public String intelligentCompress(String text, int targetTokens) {
        if (countTokens(text) <= targetTokens) {
            return text;
        }

        // Multi-stage compression
        var stage1 = removeDuplicatesAndRedundancy(text);
        var stage2 = extractKeyInformation(stage1, targetTokens);

        return stage2;
    }

    private String removeDuplicatesAndRedundancy(String text) {
        // Remove duplicate sentences and phrases
        var sentences = SENTENCE_PATTERN.split(text);
        var uniqueSentences = Arrays.stream(sentences)
                .distinct()
                .collect(Collectors.toList());

        var result = String.join(". ", uniqueSentences);

        // Remove common redundant phrases
        return DUPLICATE_PATTERN.matcher(result).replaceAll("$1");
    }

    private String extractKeyInformation(String text, int targetTokens) {
        var sentences = SENTENCE_PATTERN.split(text);
        var scoredSentences = Arrays.stream(sentences)
                .map(this::scoreSentenceImportance)
                .sorted(Comparator.comparing(ScoredSentence::score).reversed())
                .toList();

        var result = new StringBuilder();
        var currentTokens = 0;

        for (var sentence : scoredSentences) {
            int sentenceTokens = countTokens(sentence.text());
            if (currentTokens + sentenceTokens <= targetTokens) {
                result.append(sentence.text()).append(". ");
                currentTokens += sentenceTokens;
            }
        }

        return result.toString().trim();
    }

    private ScoredSentence scoreSentenceImportance(String sentence) {
        double score = 0.0;

        // Boost sentences with character names
        if (containsCharacterNames(sentence)) score += 2.0;

        // Boost action sentences
        if (containsActionWords(sentence)) score += 1.5;

        // Boost dialogue
        if (sentence.contains("\"")) score += 1.0;

        // Reduce score for purely descriptive sentences
        if (isPurelyDescriptive(sentence)) score -= 0.5;

        // Boost sentences with emotional content
        if (containsEmotionalWords(sentence)) score += 1.0;

        return new ScoredSentence(sentence, score);
    }

    private String formatGlossary(List<ObjectName> glossary) {
        return glossary.stream()
                .map(ObjectName::shortStringValue)
                .collect(Collectors.joining("\n"));
    }

    private int calculateAvailableTokens(Resource systemTemplate) {
        int systemTokens = countTokens(systemTemplate);
        return MAX_TOKENS - systemTokens - SYSTEM_TEMPLATE_RESERVE - TARGET_RESPONSE_RESERVE;
    }

    // Helper methods
    private boolean containsCharacterNames(String sentence) {
        // Implementation would check against known character names
        return sentence.matches(".*\\b[A-Z][a-z]+\\b.*");
    }

    private boolean containsActionWords(String sentence) {
        var actionWords = Set.of("attacked", "fought", "ran", "jumped", "cast", "summoned", "defeated");
        return actionWords.stream().anyMatch(sentence.toLowerCase()::contains);
    }

    private boolean isPurelyDescriptive(String sentence) {
        var descriptiveWords = Set.of("beautiful", "magnificent", "ancient", "towering", "vast");
        return descriptiveWords.stream().anyMatch(sentence.toLowerCase()::contains)
                && !sentence.contains("\"");
    }

    private boolean containsEmotionalWords(String sentence) {
        var emotionalWords = Set.of("angry", "sad", "happy", "excited", "afraid", "determined");
        return emotionalWords.stream().anyMatch(sentence.toLowerCase()::contains);
    }

    // Data classes
    private record ScoredSentence(String text, double score) {
    }

    @Data
    @Builder
    public static class ContextBundle {

        @Singular
        private final List<ContextEntry> entries;
        private final int totalTokens;
        private final int maxTokens;
        private final Resource systemMessage;

        public List<Message> toMessages() {
            var messages = new ArrayList<Message>();
            messages.add(new SystemMessage(systemMessage));

            for (var entry : entries) {
                messages.add(new UserMessage(entry.getUserPrompt()));
                messages.add(new AssistantMessage(entry.getContent()));
            }

            return messages;
        }

    }

    @Data
    @AllArgsConstructor
    public static class ContextEntry {

        private final HistoryItem type;
        private final String content;
        private final int tokens;

        public static ContextEntry context(String content, int tokens) {
            return new ContextEntry(HistoryItem.CONTEXT, content, tokens);
        }

        public static ContextEntry glossary(String content, int tokens) {
            return new ContextEntry(HistoryItem.GLOSSARY, content, tokens);
        }

        public static ContextEntry consolidatedContext(String content, int tokens) {
            return new ContextEntry(HistoryItem.CONSOLIDATED_CONTEXT, content, tokens);
        }

        public static ContextEntry consolidatedGlossary(String content, int tokens) {
            return new ContextEntry(HistoryItem.CONSOLIDATED_GLOSSARY, content, tokens);
        }

        public String getUserPrompt() {
            return switch (type) {
                case CONTEXT -> "Provide brief information about the previous web novel's chapter";
                case GLOSSARY -> "Provide a glossary list for previous novels' chapter";
                case CONSOLIDATED_CONTEXT -> "Provide brief information about the last web novel's chapters";
                case CONSOLIDATED_GLOSSARY -> "Provide a glossary list for the last web novel's chapters";
                default -> throw new AppIllegalStateException("Unexpected value: " + type);
            };
        }

    }

    @Data
    @AllArgsConstructor
    public static class TokenBudget {

        @Getter
        private final int total;

        private int remaining;

        public static TokenBudget from(int total) {
            return new TokenBudget(total, total);
        }

        public static TokenBudget defaultOne() {
            return from(10_000);
        }

        public boolean canAllocate(int tokens) {
            return remaining >= tokens;
        }

        public TokenBudget allocate(int tokens) {
            remaining = Math.max(0, remaining - tokens);

            return this;
        }

        public int remaining() {
            return remaining;
        }

    }

}
