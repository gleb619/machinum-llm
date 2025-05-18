package machinum.extract;

import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.ChunkSupport;
import machinum.processor.core.PreconditionSupport;
import machinum.flow.FlowContext;
import machinum.flow.FlowSupport;
import machinum.tool.RawInfoTool;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryExtractor implements ChunkSupport, FlowSupport, PreconditionSupport {

    @Value("${app.summary.temperature}")
    protected final Double temperature;
    @Value("${app.summary.numCtx}")
    protected final Integer contextLength;
    @Value("classpath:prompts/custom/system/SummarySystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/Summary.ST")
    private final Resource summaryTemplate;
    @Getter
    @Value("${app.summary.model}")
    private final String chatModel;
    private final Assistant assistant;

    private final RawInfoTool rawInfoTool;

    private final RetryHelper retryHelper;


    public FlowContext<Chapter> simpleExtract(FlowContext<Chapter> flowContext) {
        return doExtractSummary(flowContext, false);
    }

    public FlowContext<Chapter> extractSummary(FlowContext<Chapter> flowContext) {
        return doExtractSummary(flowContext, true);
    }

    private FlowContext<Chapter> doExtractSummary(FlowContext<Chapter> flowContext, boolean createHistory) {
        var text = flowContext.text();
        var awaitedLines = String.valueOf(Math.min((int) Math.ceil(countLines(text) / 6.5), 25));
        log.debug("Prepare to summarize text to fit the content window: text={}..., awaitedLines={}", toShortDescription(text), awaitedLines);

        List<Message> history;
        if(createHistory) {
            history = fulfillHistory(systemTemplate, flowContext);
        } else {
            history = List.of(new SystemMessage(systemTemplate));
        }

        var contextResult = doAction(flowContext, text, history, awaitedLines);

        //TODO add check and retry for short list
        String result = parseResult(contextResult);

        log.debug("Prepared summary chunks text to fit the content window: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::contextArg, FlowContextActions.context(result));
    }

    private AssistantContext.Result doAction(FlowContext<Chapter> flowContext, String text, List<Message> history, String awaitedLines) {
        var context = AssistantContext.builder()
                .flowContext(flowContext)
                .operation("extractSummary-%s-".formatted(flowContext.iteration()))
                .text(text)
                .actionResource(summaryTemplate)
                .history(history)
                .inputs(Map.of(
                        "count", awaitedLines
                ))
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(countTokens(text), history, contextLength));
                    return options;
                })
                .build();

        return retryHelper.withSmallRetry(text, retryChunk -> {
            var contextResult = requiredNotEmpty(context.copy(b -> b.text(retryChunk)), assistant::process);
            String result = requireNotEmpty(parseResult(contextResult));
            contextResult.replaceResult(result);

            return contextResult;
        });
    }

    private String parseResult(AssistantContext.Result contextResult) {
        var result = contextResult.result();
        var lines = result.lines().collect(Collectors.toList());

        return String.join("\n", truncateFromHead(lines, 25));
    }

}
