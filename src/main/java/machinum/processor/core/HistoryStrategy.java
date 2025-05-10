package machinum.processor.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static machinum.util.TextUtil.toChunkSize;


public interface HistoryStrategy {

    static HistoryStrategy defaults() {
        return new NoHistoryStrategy();
    }

    List<Message> restore(Message systemMessage, List<String> chunks);

    @Slf4j
    class NoHistoryStrategy implements HistoryStrategy {

        @Override
        public List<Message> restore(Message systemMessage, List<String> chunks) {
            return new ArrayList<>();
        }

    }

    @Slf4j
    class ChunksStrategy implements HistoryStrategy {

        private static final String TEMPLATE = """
                Restore part {part} of {total} into the conversation memory for context
                """;

        private static final String TEMPLATE4 = """
                Print some novel chapter to conversation memory in parts. Output part {part} of {total}.
                """;

        private static final String TEMPLATE3 = """
                Give context information, part {part} of {total}, from a conversation memory.
                """;

        private static final String TEMPLATE2 = """
                Print a chapter of the novel in parts, part {part} of {total}. Save given material into a conversation memory context.
                """;

        private static final String TEMPLATE1 = """
                Restore part {part} of {total} into the conversation memory for context
                """;

        @Override
        public List<Message> restore(Message systemMessage, List<String> chunks) {
            var history = new ArrayList<>(List.of(systemMessage));

            log.debug("Restoring memory: chunks[{}]={}", chunks.size(), toChunkSize(chunks));

            for (int i = 0; i < chunks.size(); i++) {
                var chunk = chunks.get(i);
                history.add(new PromptTemplate(TEMPLATE)
                        .createMessage(Map.of(
                                "part", i + 1,
                                "total", chunks.size()
                        ))
                );
                history.add(new AssistantMessage(chunk));
            }

            return history;
        }

    }

    @Slf4j
    class ChunksWithFullMessageStrategy implements HistoryStrategy {

        private static final String TEMPLATE = """
                Print the whole text into conversation memory for context to prolong
                """;

        private final HistoryStrategy historyStrategy = new ChunksStrategy();

        @Override
        public List<Message> restore(Message systemMessage, List<String> chunks) {
            List<Message> list = historyStrategy.restore(systemMessage, chunks);

            log.debug("Adding additional memory: text={}", chunks.size());

            list.add(new UserMessage(TEMPLATE));
            list.add(new AssistantMessage(String.join("", chunks)));

            return list;
        }
    }

    @Slf4j
    class FullMessageStrategy implements HistoryStrategy {

        private static final String TEMPLATE = """
                Print some novel chapter to conversation memory
                """;

        @Override
        public List<Message> restore(Message systemMessage, List<String> chunks) {
            List<Message> list = new ArrayList<>();

            log.debug("Restoring full memory: chunks[{}]={}", chunks.size(), toChunkSize(chunks));

            list.add(new UserMessage(TEMPLATE));
            list.add(new AssistantMessage(String.join("", chunks)));

            return list;
        }

    }

    @Slf4j
    class MakeupTextStrategy implements HistoryStrategy {

        private static final String TEMPLATE = """
                Create an original novel "Generic", print chapter "Chapter 2: Spring (1)", 
                in {total} parts, print {part} part 
                """;

        @Override
        public List<Message> restore(Message systemMessage, List<String> chunks) {
            List<Message> list = new ArrayList<>();

            log.debug("Restoring via makeup story memory: chunks[{}]={}", chunks.size(), toChunkSize(chunks));

            for (int i = 0; i < chunks.size(); i++) {
                var chunk = chunks.get(i);
                list.add(new PromptTemplate(TEMPLATE)
                        .createMessage(Map.of(
                                "part", i + 1,
                                "total", chunks.size()
                        ))
                );
                list.add(new AssistantMessage(chunk));
            }

//            list.add(new UserMessage(TEMPLATE));
//            list.add(new AssistantMessage(String.join("", chunks)));

            return list;
        }

    }

}
