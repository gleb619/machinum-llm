package machinum.extract;

import machinum.model.Chapter;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.ChunkSupport;
import machinum.flow.FlowContext;
import machinum.flow.FlowSupport;
import machinum.tool.RawInfoTool;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static machinum.flow.FlowContext.text;
import static machinum.flow.FlowSupport.HistoryItem.*;
import static machinum.flow.FlowSupport.HistoryItem.CONSOLIDATED_GLOSSARY;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class Rewriter implements ChunkSupport, FlowSupport {

    @Value("${app.rewrite.temperature}")
    protected final Double temperature;
    @Value("${app.rewrite.numCtx}")
    protected final Integer contextLength;
    protected final RetryHelper retryHelper;
    @Value("classpath:prompts/custom/system/RewriteSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/Rewrite.ST")
    private final Resource rewriteTemplate;
    @Getter
    @Value("${app.rewrite.model}")
    private final String chatModel;
    private final Assistant assistant;
    private final RawInfoTool rawInfoTool;

    public FlowContext<Chapter> rewrite(FlowContext<Chapter> flowContext) {
        String text = flowContext.text();

        log.debug("Rewriting story for given: text={}", toShortDescription(text));

        var textTokens = countTokens(flowContext.text());
        var history = prepareHistory(flowContext, textTokens);

        String result = doAction(flowContext, text, history, textTokens);

        log.debug("Rewritten text to fit the content window: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::textArg, text(result));
    }

    private String doAction(FlowContext<Chapter> flowContext, String text, List<Message> history, Integer textTokens) {
        var assistantContext = AssistantContext.builder()
                .flowContext(flowContext)
                .operation("rewrite-%s-".formatted(flowContext.iteration()))
                .text(text)
                .actionResource(rewriteTemplate)
                .history(history)
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(textTokens, history, contextLength));

                    return options;
                })
                .build();
        var context = retryHelper.withRetry(text, retryChunk ->
                assistant.process(assistantContext.copy(b -> b.text(retryChunk))));

        return context.result();
    }

    private List<Message> prepareHistory(FlowContext<Chapter> flowContext, Integer textTokens) {
        int targetLength = textTokens * 2;
        var historySettings = new ArrayList<HistoryItem>();

        //If we have space, then we try to add base context
        if (calculatePercent(targetLength, contextLength) <= 70) {
            historySettings.add(CONTEXT);
            historySettings.add(GLOSSARY);
        }

        //If we have space, then we try to add extended context
        if (calculatePercent(targetLength, contextLength) <= 60) {
            historySettings.add(CONSOLIDATED_CONTEXT);
            historySettings.add(CONSOLIDATED_GLOSSARY);
        }

//        List<Message> history;
//        //For small models(in context length way), like gemma we're forced to cut the data for history
//        if(targetLength > (contextLength * 0.7)) {
//            history = new ArrayList<>();
//            history.add(new SystemMessage(systemTemplate));
//
//            flowContext.hasAnyArgument(oldContext -> {
//                var contextText = FlowSupport.compressContext(oldContext, targetLength, systemTemplate, contextLength);
//
//                if(!contextText.isBlank()) {
//                    history.add(new UserMessage(USER_PREVIOUS_CONTEXT_TEMPLATE));
//                    history.add(new AssistantMessage(contextText));
//                }
//            }, FlowContext::consolidatedContextArg, FlowContext::contextArg, FlowContext::oldTextArg, FlowContext::oldContexArg);
//        } else {
//            history = fulfillHistory(systemTemplate, flowContext, historySettings);
//        }
//
//        return history;
        return fulfillHistory(systemTemplate, flowContext, historySettings);
    }

}
