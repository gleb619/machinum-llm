package machinum.processor.client;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.processor.core.AssistantContext;
import machinum.util.TextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiClient implements AiClient {

    public static final String USER_ROLE = "user";
    public static final String MODEL_ROLE = "model";
    private static final int MAX_RETRIES = 2;
    private final GeminiClientPool clientPool;

    @Value("${spring.ai.gemini-ai.chat.options.model:gemini-2.0-flash-exp}")
    private final String model;

    @Override
    public AssistantMessage call(AssistantContext assistantContext, Prompt prompt) {
        log.info("|-->> Executing gemini request with context: {}", assistantContext);
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                var item = clientPool.getAvailableClient();
                var context = buildPromptContent(prompt);

                var response = item.execute(client -> client.models.generateContent(model, context.messages(), context.config()));
                var responseText = Objects.requireNonNull(response.text(), "Response text can't be null");

                log.info("|<<-- Successfully executed Gemini request: {}", TextUtil.toShortDescription(responseText));

                return new AssistantMessage(responseText);
            } catch (AppIllegalStateException e) {
                log.error("|X-- Error during Gemini execution: {}|{}", e.getClass(), e.getMessage());
                lastException = e;
                String errorMessage = e.getMessage();

                if (isQuotaError(errorMessage)) {
                    try {
                        var failedItem = clientPool.getAvailableClient();
                        clientPool.handleQuotaError(failedItem, errorMessage);
                    } catch (RuntimeException poolException) {
                        throw new AppIllegalStateException("All Gemini clients exhausted quota limits", e);
                    }
                } else {
                    throw new AppIllegalStateException("Gemini API error: %s".formatted(errorMessage), e);
                }
            } catch (Exception e) {
                log.error("|X-- Error during Gemini execution: {}|{}", e.getClass(), e.getMessage());
                lastException = e;
                if (attempt == MAX_RETRIES - 1) {
                    throw new AppIllegalStateException("Error calling Gemini API after %d attempts".formatted(MAX_RETRIES), e);
                }
            }
        }

        throw new RuntimeException("Failed to get response after %d attempts".formatted(MAX_RETRIES), lastException);
    }

    @Override
    public Provider getProvider() {
        return Provider.GEMINI_AI;
    }

    private GeminiContext buildPromptContent(Prompt prompt) {
        var history = new ArrayList<>(prompt.getInstructions());
        var systemMessage = history.removeFirst();
        var systemInstruction = Content.fromParts(Part.fromText(systemMessage.getText()));

        List<Content> contents = new ArrayList<>();
        for (var message : history) {
            var builder = Content.builder();

            if (message instanceof UserMessage um) {
                builder = builder.role(USER_ROLE)
                        .parts(Part.fromText(um.getText()));
            } else if (message instanceof AssistantMessage am) {
                builder = builder.role(MODEL_ROLE)
                        .parts(Part.fromText(am.getText()));
            } else {
                throw new AppIllegalStateException("Message type[%s] is not supported yet!", message.getClass());
            }

            contents.add(builder.build());
        }

        var config = GenerateContentConfig
                .builder()
                // Sets the thinking budget to 0 to disable thinking mode
                .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0))
                .candidateCount(1)
                .maxOutputTokens(20480)
                .systemInstruction(systemInstruction)
                .build();

        return new GeminiContext(contents, config);
    }

    private boolean isQuotaError(String errorMessage) {
        return errorMessage != null && (
                errorMessage.contains("quota") ||
                        errorMessage.contains("RESOURCE_EXHAUSTED") ||
                        errorMessage.contains("429") ||
                        errorMessage.contains("QuotaFailure")
        );
    }

    private record GeminiContext(List<Content> messages, GenerateContentConfig config) {
    }

}
