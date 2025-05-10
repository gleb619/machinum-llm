package machinum.extract;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static machinum.flow.FlowContext.context;
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


    public FlowContext<Chapter> extractSummary(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        var awaitedLines = String.valueOf(Math.min((int) Math.ceil(countLines(text) / 6.5), 25));
        log.debug("Prepare to summarize text to fit the content window: text={}..., awaitedLines={}", toShortDescription(text), awaitedLines);

        var history = fulfillHistory(systemTemplate, flowContext);

        var contextResult = doAction(flowContext, text, history, awaitedLines);

        //TODO add check and retey for short list
        String result = parseResult(contextResult);

        log.debug("Prepared summary chunks text to fit the content window: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::contextArg, context(result));
    }

    private AssistantContext.Result doAction(FlowContext<Chapter> flowContext, String text, List<Message> history, String awaitedLines) {
        var context = AssistantContext.builder()
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
