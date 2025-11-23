package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.FlowContext;
import machinum.model.Chapter;
import machinum.processor.client.AiClient;
import machinum.processor.core.*;
import machinum.processor.core.AssistantContext.AssistantContextBuilder;
import machinum.tool.RawInfoTool;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static machinum.processor.HistoryService.TokenBudget.from;

@Slf4j
@Component
@RequiredArgsConstructor
public class Worker implements ChunkSupport, FlowSupport, PreconditionSupport, XmlSupport, ObjectNameSupport {

    @Value("${app.default.numCtx}")
    protected final Integer contextLength;
    @Value("${app.default.model}")
    private final String defaultModel;
    @Value("${app.default.temperature}")
    private final Double defaultTemperature;
    @Value("${app.default.provider}")
    private final String defaultProvider;

    private final Assistant assistant;
    private final RetryHelper retryHelper;

    /**
     * saved for backward compatibility only, no longer in use
     */
    @Deprecated
    private final RawInfoTool rawInfoTool;

    //TODO: add javadoc
    public Function<AssistantContextBuilder, AssistantContextBuilder> fromFlow(FlowContext<Chapter> flowContext,
                                                                               Resource systemTemplate,
                                                                               HistoryItem... historyItems) {
        return builder -> builder.flowContext(flowContext)
                .text(flowContext.text())
                .history(createAdvancedHistory(HistoryContext.builder()
                        .systemMessage(systemTemplate)
                        .flowContext(flowContext)
                        .allowedItems(List.of(historyItems))
                        .budget(from(contextLength).allocate(systemTemplate)
                                .allocate(flowContext.textArg().countTokens()))
                        .build()));
    }

    //TODO: add javadoc
    //Example of usage: `worker.createContext(worker.fromFlow(flowContext, systemTemplate, HistoryItem.CONSOLIDATED_CONTEXT));`
    public AssistantContext createContext(Function<AssistantContextBuilder, AssistantContextBuilder> customizer) {
        return customizer.apply(AssistantContext.builder())
                .build();
    }

    /**
     * Gets the sliding context window for given params.
     */
    public int getSlidingWindow(Integer textTokens, List<Message> history, int contextLength) {
        return FlowSupport.slidingContextWindow(textTokens, history, contextLength);
    }

    /**
     * Creates history with system template only.
     */
    @SuppressWarnings("unchecked")
    public <T> List<Message> createSystemHistory(FlowContext<T> flowContext, Resource systemTemplate) {
        return fulfillHistory(systemTemplate, (FlowContext<Chapter>) flowContext);
    }

    /**
     * Creates history with system template and specific allowed items.
     */
    @SuppressWarnings("unchecked")
    public <T> List<Message> createCustomHistory(FlowContext<T> flowContext, Resource systemTemplate, HistoryItem... allowedItems) {
        return fulfillHistory(systemTemplate, (FlowContext<Chapter>) flowContext, allowedItems);
    }

    /**
     * Creates history from HistoryContext for complex scenarios.
     */
    public <T> List<Message> createAdvancedHistory(HistoryContext historyContext) {
        return fulfillHistory(historyContext);
    }

    /**
     * Executes retry logic with small retry policy.
     */
    protected AssistantContext.Result retrySmall(String text, Function<String, AssistantContext.Result> processor) {
        return retryHelper.withSmallRetry(text, processor);
    }

    /**
     * Executes retry logic with full retry policy.
     */
    protected AssistantContext.Result retryFull(String text, Function<String, AssistantContext.Result> processor) {
        return retryHelper.withRetry(text, processor);
    }

    /**
     * Executes retry logic with small retry, handling LengthValidationException specially.
     */
    protected AssistantContext.Result retrySmallWithFallback(String text, Function<String, AssistantContext.Result> processor, AssistantContext context) {
        try {
            return retrySmall(text, processor);
        } catch (PreconditionSupport.LengthValidationException e) {
            return AssistantContext.Result.of(context.getMostResultFromHistory());
        }
    }

    /**
     * Creates a retry processor function that handles assistant processing and post-processing.
     */
    protected Function<String, AssistantContext.Result> createRetryProcessor(AssistantContext baseContext,
                                                                             Function<AssistantContext.Result, AssistantContext.Result> postProcessor) {
        return retryChunk -> {
            AssistantContext context = baseContext.copy(b -> b.text(retryChunk));
            AssistantContext.Result result = assistant.process(context);
            return postProcessor != null ? postProcessor.apply(result) : result;
        };
    }

    /**
     * Executes action with the given context, adding defaults if incomplete.
     */
    public AssistantContext.Result work(AssistantContext context, String operationName,
                                        Function<AssistantContext.Result, AssistantContext.Result> postProcessor,
                                        RetryType retryType, AssistantContext fallbackContext,
                                        Function<AssistantContext, Function<String, AssistantContext.Result>> customRetryProcessorFactory) {

        // Set operation name
        context.setOperation(operationName);
        if (Objects.isNull(context.getProvider()) ||
                Objects.equals(context.getProvider(), AiClient.Provider.NONE)) {
            context.setProvider(AiClient.Provider.parse(defaultProvider));
        }

        // Create processor
        Function<String, AssistantContext.Result> processor;
        if (customRetryProcessorFactory != null) {
            processor = customRetryProcessorFactory.apply(context);
        } else {
            processor = createRetryProcessor(context, postProcessor);
        }

        // Execute based on retry type
        var finalFallbackContext = fallbackContext;
        if (retryType == RetryType.SMALL_WITH_FALLBACK && finalFallbackContext == null) {
            finalFallbackContext = context;
        }
        return switch (retryType) {
            case SMALL -> retrySmall(context.getText(), processor);
            case FULL -> retryFull(context.getText(), processor);
            case SMALL_WITH_FALLBACK -> retrySmallWithFallback(context.getText(), processor, finalFallbackContext);
        };
    }

    /**
     * Sugar method: Executes action with SMALL or FULL retry, no fallback, no custom processor.
     */
    public AssistantContext.Result work(AssistantContext context, String operationName,
                                        Function<AssistantContext.Result, AssistantContext.Result> postProcessor,
                                        RetryType retryType) {
        return work(context, operationName, postProcessor, retryType, null, null);
    }

    /**
     * Sugar method: Executes action with SMALL_WITH_FALLBACK retry, custom processor, no postProcessor.
     */
    public AssistantContext.Result work(AssistantContext context, String operationName,
                                        RetryType retryType, Function<AssistantContext, Function<String, AssistantContext.Result>> customRetryProcessorFactory) {
        return work(context, operationName, null, retryType, null, customRetryProcessorFactory);
    }

    public enum RetryType {
        SMALL, FULL, SMALL_WITH_FALLBACK
    }

}
