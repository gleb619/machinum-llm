package machinum.processor.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.JavaUtil.calculateReductionForChunks;
import static machinum.util.TextUtil.*;

public interface CompressStrategy {

    List<Message> compress(List<Message> history);

    @Deprecated
    @Slf4j
//    @Component
    @RequiredArgsConstructor
//    @ConditionalOnProperty(name = "app.compress.mode", havingValue = "simple")
    class ChainCompressStrategy implements CompressStrategy {

        protected final Integer contextSize;
        protected final Integer compressPercentage;
        private final Resource compressTemplate;
        private final Resource restoreTemplate;
        private final Assistant assistant;

        @Override
        public List<Message> compress(List<Message> history) {
            var localHistory = new ArrayList<>(history);
            var historyTokenCount = countHistoryTokens(localHistory);
            log.warn("An excess of the allowed context limit has been detected, compression will be performed: history={}, limit={}, diff={}",
                    historyTokenCount, contextSize, (contextSize - historyTokenCount));

//            var history = new ArrayList<Message>();
            var oldSystem = localHistory.removeFirst();
            var resultBuilder = new StringBuilder();

            //If history is not even we add sample message
            if ((localHistory.size() % 2) != 0) {
                localHistory.add(new UserMessage("1 + 1"));
            }

            for (int i = 0; i < localHistory.size(); i = i + 2) {
                var compressed = compressPart(localHistory.get(i), localHistory.get(i + 1));
                resultBuilder.append(compressed).append("\n");
            }

            var compressedHistory = resultBuilder.toString();
            var restore = new UserMessage(restoreTemplate);
            var context = new AssistantMessage(compressedHistory);

            return List.of(oldSystem, restore, context);
        }

        private String compressPart(Message userMsg, Message assistantMsg) {
            var text = "User: %s\nAI: %s".formatted(userMsg.getText(), assistantMsg.getText());

            var builder = new StringBuilder();
            var processorContext = assistant.process(
                    AssistantContext.builder()
                            .operation("compress")
                            .text(text)
                            .actionResource(compressTemplate)
                            .inputs(Map.of(
                                    "percentage", compressPercentage + ""
                            ))
                            .build());

            return processorContext.result(s -> s.replaceAll("^AI: ", ""));
        }

    }

    @Deprecated
    @Slf4j
    @RequiredArgsConstructor
    class SimpleCompressStrategy implements CompressStrategy {

        protected final Integer contextSize;
        protected final Integer compressPercentage;
        private final Resource compressTemplate;
        private final Resource restoreTemplate;
        private final Assistant assistant;


        @Override
        public List<Message> compress(List<Message> history) {
            var localHistory = new ArrayList<>(history);
            var historyTokenCount = countHistoryTokens(localHistory);
            log.warn("An excess of the allowed context limit has been detected, compression will be performed: history={}, limit={}, diff={}",
                    historyTokenCount, contextSize, (contextSize - historyTokenCount));

//            List<Double> percentages = calculateReductionForChunks(assistantChunks(history), contextSize);

            var percentageOfCompress = calcCompressionPercent(historyTokenCount);

            var result = assistant.process(
                            AssistantContext.builder()
                                    .operation("compress")
                                    .text("")
                                    .actionResource(compressTemplate)
                                    .inputs(Map.of(
                                            "percentage", percentageOfCompress + ""
                                    ))
                                    .history(history)
                                    .build())
                    .result();

            var oldSystem = localHistory.removeFirst();
            var restore = new UserMessage(restoreTemplate);
            var context = new AssistantMessage(result);

            return List.of(oldSystem, restore, context);
        }

        protected int calcCompressionPercent(Integer historyTokenCount) {
            int compress;
            if (historyTokenCount >= contextSize) {
                compress = compressPercentage;
            } else {
                compress = (int) (compressPercentage - calculatePart(historyTokenCount, contextSize));
            }

            return compress;
        }

    }

    @Slf4j
    @RequiredArgsConstructor
    class AdvancedCompressStrategy implements CompressStrategy {

        private static final String USER_PROMPT = "Restore context for conversation memory";

        private static final String USER_IN_NEW_HISTORY_PROMPT = USER_PROMPT + ", part %s of %s";
        protected final Integer availableContextSize;
        private final Resource compressTemplate;
        private final Assistant assistant;

        @Override
        public List<Message> compress(List<Message> history) {
            var localHistory = new ArrayList<>(history);
            var historyTokenCount = countHistoryTokens(localHistory);
            log.warn("An excess of the allowed context limit has been detected, compression will be performed: history={}, limit={}, diff={}",
                    historyTokenCount, availableContextSize, (availableContextSize - historyTokenCount));

            var newHistory = new ArrayList<Message>();
            newHistory.add(history.getFirst());

            var assistantChunks = assistantChunks(history);
            var percentages = calculateReductionForChunks(assistantChunks, availableContextSize);

            log.debug("Compressing chunks to reduce: assistantChunks[{}]={} tokens, percentage to reduce[{}]={}",
                    assistantChunks.size(), countTokens(assistantChunks), percentages.size(), percentages
            );

            for (int i = 0; i < assistantChunks.size(); i++) {
                processChunk(assistantChunks, i, percentages, newHistory);
            }

            return newHistory;
        }

        private void processChunk(List<String> assistantChunks, int index, List<Double> percentages, List<Message> newHistory) {
            var assistantChunk = assistantChunks.get(index);

            var percent = Math.round(percentages.get(index));
            var chunkTokens = countTokensFast(assistantChunk);
            log.debug("Compressing given chunk: chunk={}..., tokens={}, percent={}", toShortDescription(assistantChunk), chunkTokens, percent);

            var result = assistant.process(
                            AssistantContext.builder()
                                    .operation("compress")
                                    .text(assistantChunk)
                                    .actionResource(compressTemplate)
                                    .inputs(Map.of(
                                            "percentage", percent + ""
                                    ))
                                    .history(List.of())
                                    .build())
                    .result();

            var resultTokens = countTokensFast(result);
            log.debug("Compressed chunk: result={}..., tokens={}, diff={}", toShortDescription(result), resultTokens, (chunkTokens - resultTokens));

            newHistory.add(new UserMessage(USER_IN_NEW_HISTORY_PROMPT.formatted(index + 1, assistantChunks.size())));
            newHistory.add(new AssistantMessage(result));
        }
//        private void processChunk(List<String> assistantChunks, int index, List<Double> percentages, ArrayList<Message> newHistory) {
//            var assistantChunk = assistantChunks.get(index);
//            var chunksHistory = List.<Message>of(
//                    new UserMessage(USER_PROMPT),
//                    new AssistantMessage(assistantChunk)
//            );
//
//            var percent = Math.round(percentages.get(index));
//            log.debug("Compressing given chunk: chunk={}, percent={}", toShortDescription(assistantChunk), percent);
//
//            var result = assistant.process(
//                    AssistantContext.builder()
//                            .operation("compress")
//                            .actionResource(compressTemplate)
//                            .inputs(Map.of(
//                                    "percentage", percent + ""
//                            ))
//                            .history(chunksHistory)
//                            .build())
//                    .result();
//            newHistory.add(new UserMessage(USER_IN_NEW_HISTORY_PROMPT.formatted(index + 1, assistantChunks.size())));
//            newHistory.add(new AssistantMessage(result));
//        }

    }

    @Slf4j
    @RequiredArgsConstructor
    class NoneStrategy implements CompressStrategy {

        @Override
        public List<Message> compress(List<Message> history) {
            return history;
        }

    }

}
