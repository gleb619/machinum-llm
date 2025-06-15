package machinum.processor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextArgs;
import machinum.flow.FlowException;
import machinum.util.TextUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import swiss.ameri.gemini.api.Content;
import swiss.ameri.gemini.api.GenAi;
import swiss.ameri.gemini.api.GenerativeModel;
import swiss.ameri.gemini.api.ModelVariant;
import swiss.ameri.gemini.spi.JsonParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static machinum.flow.FlowContextActions.warning;
import static machinum.processor.core.ChapterWarning.createNew;

/**
 * A client for interacting with the Gemini AI service, implementing the {@link AiClient} interface.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "production")
public class GeminiClient implements AiClient {

    private final GenAi genAi;

    @Value("${spring.ai.gemini.chat.options.model}")
    private final String modelName;

    @Value("${spring.ai.ollama.init.timeout}")
    private final Duration timeout;

    /**
     * Calls the Gemini AI service with the provided context and prompt to generate an assistant message.
     *
     * @param assistantContext The context in which the assistant operates.
     * @param prompt           The user's input prompt for generating a response.
     * @return An {@link AssistantMessage} containing the AI-generated response.
     */
    @Override
    @SneakyThrows
    public AssistantMessage call(AssistantContext assistantContext, Prompt prompt) {
        var model = parsePrompt(prompt);

        log.info("|-->> Executing gemini request with context: {}", assistantContext);
        GenAi.GeneratedContent content = null;
        try {
            content = execute(model, assistantContext)
                    .get(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if(e.getCause() instanceof FlowException) {
                ExceptionUtils.rethrow(e.getCause());
            } else {
                ExceptionUtils.rethrow(e);
            }
        }

        return new AssistantMessage(content.text());
    }

    /**
     * Executes the generation of content using the specified generative model.
     *
     * @param model            The generative model to use for content generation.
     * @param ctx
     * @return A {@link CompletableFuture} representing the generated content.
     */
    private CompletableFuture<GenAi.GeneratedContent> execute(GenerativeModel model, AssistantContext ctx) {
        return genAi.generateContent(model)
                .exceptionally(ex -> {
                    handleException(ctx, ex);

                    return null;
                })
                .whenComplete((result, e) -> {
                    if (Objects.nonNull(e)) {
                        log.error("Error during Gemini execution: {}|{}", e.getClass(), e.getMessage());
                    } else {
                        log.info("Successfully executed Gemini request: {}", TextUtil.toShortDescription(result.text()));
                    }
                });
    }

    @Override
    public Provider getProvider() {
        return Provider.GEMINI;
    }

    /**
     * Parses the provided prompt into a generative model.
     *
     * @param prompt The user's input prompt.
     * @return A {@link GenerativeModel} built from the prompt.
     */
    private GenerativeModel parsePrompt(Prompt prompt) {
        var builder = GenerativeModel.builder();
        var history = new ArrayList<>(prompt.getInstructions());
        var systemMessage = history.removeFirst();

        builder.modelName(parseModel(modelName))
                .addContent(new Content.TextContent(
                        Content.Role.USER.roleName(),
                        "Provide instructions for our task"
                ))
                .addContent(new Content.TextContent(
                        Content.Role.MODEL.roleName(),
                        systemMessage.getText()
                ));

        for (var message : history) {
            var type = (message instanceof UserMessage) ? Content.Role.USER.roleName() : Content.Role.MODEL.roleName();

            builder.addContent(
                    new Content.TextContent(type, message.getText())
            );
        }

        return builder.build();
    }

    /**
     * Parses the model name string into a corresponding {@link ModelVariant}.
     *
     * @param modelName The name of the model.
     * @return A {@link ModelVariant} that matches the provided model name.
     */
    private ModelVariant parseModel(@NonNull String modelName) {
        var localModelName = modelName.toLowerCase();
        for (var modelVariant : ModelVariant.values()) {
            if (modelVariant.variant().toLowerCase().contains(localModelName)) {
                return modelVariant;
            }
        }

        log.warn("No matching model found for name: {}. Defaulting to GEMINI_2_0_FLASH_EXP.", modelName);
        return ModelVariant.GEMINI_2_0_FLASH_EXP;
    }

    private void handleException(AssistantContext ctx, Throwable ex) {
        Throwable e;
        if (ex instanceof ExecutionException ee) {
            e = ee.getCause();
        } else {
            e = ex;
        }

        boolean isCSAM = ex.getMessage().contains("PROHIBITED_CONTENT");

        if(isCSAM) {
            String reason = """
            Got PROHIBITED_CONTENT response - probably it's a non-configurable safety filters, \
            which block child sexual abuse material (CSAM) \
            and personally identifiable information (PII)\
            """;

            var flowContext = ctx.getFlowContext().rearrange(FlowContextArgs::warningArg, warning(createNew(b -> b
                    .text(reason)
                    .type(ChapterWarning.WarningType.R18_CONTENT)
                    .metadata("exception", e.getMessage())
            )));

            //If not isCSAM then stop execution
            //@see https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/configure-safety-filters
            throw BusinessGeminiException.createNew(b -> b
                    .message("Found R21 content, execution is prohibited")
                    .cause(e)
                    .shouldStopExecution(false)
                    .flowContext(flowContext)
                    .reason(reason)
            );
        } else {
            throw BusinessGeminiException.createNew(b -> b
                    .message("Business error: " + e.getMessage())
                    .cause(e));
        }

    }

    /**
     * A JSON parser implementation using Jackson's ObjectMapper.
     */
    @RequiredArgsConstructor
    public static class JacksonJsonParser implements JsonParser {

        private final ObjectMapper mapper;

        @Override
        @SneakyThrows
        public String toJson(Object o) {
            return mapper.writeValueAsString(o);
        }

        @Override
        @SneakyThrows
        public <T> T fromJson(String s, Class<T> aClass) {
            return mapper.readValue(s, aClass);
        }

    }

    public static class BusinessGeminiException extends FlowException {

        @Builder
        public BusinessGeminiException(String message, Throwable cause, boolean shouldStopExecution, String reason, FlowContext<?> flowContext) {
            super(message, cause, shouldStopExecution, reason, flowContext);
        }

        public static BusinessGeminiException createNew(Function<BusinessGeminiExceptionBuilder, BusinessGeminiExceptionBuilder> creator) {
            return creator.apply(builder()).build();
        }

    }

}