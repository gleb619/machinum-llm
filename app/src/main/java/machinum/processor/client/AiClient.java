package machinum.processor.client;

import machinum.processor.core.AssistantContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

public interface AiClient {

    AssistantMessage call(AssistantContext assistantContext, Prompt prompt);

    Provider getProvider();

    enum Provider {

        OLLAMA,

        GEMINI,

        GEMINI_AI,

        OPENROUTER,

        NONE,

        ;

        public static Provider parse(String name) {
            for (var value : values()) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }

            return OLLAMA;
        }

    }

}
