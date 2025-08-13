package machinum.processor.core;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.processor.client.AiClient;
import machinum.util.JavaUtil;
import org.springframework.ai.autoconfigure.ollama.OllamaTransformProperties;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantClient {

    private final List<AiClient> aiClients;

    private final Holder<OllamaChatProperties> ollamaChatPropertiesHolder;

    @Deprecated
    private final Holder<OllamaTransformProperties> ollamaTransformPropertiesHolder;


    public Result call(AssistantContext assistantContext, Prompt prompt) {
        var aiClient = Objects.requireNonNull(JavaUtil.findBy(aiClients, AiClient::getProvider, assistantContext.getProvider()), "AiClient not found");
        var message = aiClient.call(assistantContext, prompt);

        return Result.builder()
                .text(message.getText())
                .message(message)
                .build();
    }

    public OllamaOptions parseOptions(AssistantContext assistantContext) {
        var chatProperties = ollamaChatPropertiesHolder.data();
        var transformProperties = ollamaTransformPropertiesHolder.data();
        var options = assistantContext.getChatType() == Assistant.Type.CHAT ? chatProperties.getOptions() : transformProperties.getOptions();
        var result = assistantContext.getCustomizeChatOptions().apply(options.copy());

        State.state().set(result);

        return result;
    }

    /* ============= */

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Result {

        private AssistantMessage message;
        private String text;

        public static Result createNew() {
            return Result.builder()
                    .build();
        }

    }

    /**
     * Used in tests, to form report
     */
    public static class State {

        private static final State INSTANCE = new State();
        private final AtomicReference<OllamaOptions> lastModel = new AtomicReference<>();

        public static State state() {
            return INSTANCE;
        }

        public void set(OllamaOptions value) {
            lastModel.getAndSet(value);
        }

        public OllamaOptions get() {
            return lastModel.get();
        }

    }

}
