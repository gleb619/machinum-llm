package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.FlowContext;
import machinum.model.Chapter;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.ChunkSupport;
import machinum.processor.core.FlowSupport;
import machinum.tool.RawInfoTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static machinum.flow.model.helper.FlowContextActions.consolidatedContext;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryConsolidator implements ChunkSupport, FlowSupport {

    @Value("${app.summary.consolidate.temperature}")
    protected final Double temperature;
    @Value("${app.summary.consolidate.numCtx}")
    protected final Integer contextLength;
    @Value("classpath:prompts/custom/system/ConsolidatedSummarySystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/ConsolidatedSummary.ST")
    private final Resource summaryTemplate;
    @Getter
    @Value("${app.summary.consolidate.model}")
    private final String chatModel;
    private final Assistant assistant;

    private final RawInfoTool rawInfoTool;

    private static String parseResult(AssistantContext.Result contextResult) {
        var result = contextResult.result();
        var lines = result.lines().collect(Collectors.toList());

        return String.join("\n", truncateFromHead(lines, 50));
    }

    private static String calcLines(String previous, String current) {
        var summaryLines = countLines(previous + "\n" + current);
        var percentile = calculatePart(70, summaryLines);
        int value = (int) Math.ceil(percentile);
        int minValue = Math.min(value, 50);

        return String.valueOf(minValue);
    }

    public FlowContext<Chapter> consolidate(FlowContext<Chapter> flowContext) {
        String result;

        if (flowContext.hasArgument(FlowContext::consolidatedContextArg)) {
            result = doAction(flowContext, flowContext.consolidatedContext(), flowContext.context());
        } else if (flowContext.hasArguments(FlowContext::oldContextArg, FlowContext::contextArg)) {
            result = doAction(flowContext, flowContext.oldContext(), flowContext.context());
        } else {
            result = flowContext.context();
        }

        return flowContext.rearrange(FlowContext::consolidatedContextArg, consolidatedContext(result));
    }

    private String doAction(FlowContext<Chapter> flowContext, String previous, String current) {
        var awaitedLines = calcLines(previous, current);

        log.debug("Prepare to consolidate summary: previous={}..., current={}, awaitedLines={}", toShortDescription(previous), toShortDescription(current), awaitedLines);

        var history = fulfillHistory(systemTemplate, flowContext);

        var contextResult = assistant.process(AssistantContext.builder()
                .flowContext(flowContext)
                .operation("consolidateSummary-%s-".formatted(flowContext.iteration()))
                .text(previous)
                .actionResource(summaryTemplate)
                .history(history)
                .inputs(Map.of(
                        "count", awaitedLines,
                        "text2", current
                ))
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(countTokens(previous + current), history, contextLength));
                    return options;
                })
                .build());

        var result = parseResult(contextResult);

        log.debug("Prepared consolidated summary: text={}...", toShortDescription(result));

        return result;
    }

}
