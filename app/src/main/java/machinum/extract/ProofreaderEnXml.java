package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.processor.core.*;
import machinum.tool.RawInfoTool;
import machinum.util.CodeBlockExtractor;
import machinum.util.TextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static machinum.flow.model.helper.FlowContextActions.text;
import static machinum.processor.client.AiClient.Provider.parse;
import static machinum.util.TextUtil.toShortDescription;
import static org.apache.commons.lang3.StringEscapeUtils.escapeXml;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProofreaderEnXml implements ChunkSupport, FlowSupport, PreconditionSupport, XmlSupport {

    @Getter
    @Value("${app.proofread.en.model}")
    protected final String chatModel;
    @Value("${app.proofread.en.temperature}")
    protected final Double temperature;
    @Value("${app.proofread.en.numCtx}")
    protected final Integer contextLength;
    @Value("${app.proofread.en.provider}")
    protected final String provider;
    @Value("classpath:prompts/custom/system/ProofreadEnSystem.xml.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/ProofreadEn.xml.ST")
    private final Resource proofreadTemplate;
    private final RawInfoTool rawInfoTool;
    private final Assistant assistant;

    private final Worker worker;


    public FlowContext<Chapter> proofread(FlowContext<Chapter> flowContext) {
        var textArg = flowContext.textArg();
        var counter = new AtomicInteger(1);
        var list = TextUtil.toParagraphs(textArg.stringValue()).stream()
                .map(s -> new OriginText(counter.getAndIncrement(), escapeXml(s), ""))
                .collect(Collectors.toList());
        var newText = convertToXml(list, "proofread", OriginText::proofread);

        return doProofread(flowContext.replace(FlowContext::textArg, text(newText)))
                .replace(FlowContext::textArg, textArg);
    }

    private FlowContext<Chapter> doProofread(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        var textTokens = flowContext.textArg().countTokens();

        log.debug("Proofreading story for given: text={}", toShortDescription(text));

        var history = worker.createCustomHistory(flowContext, systemTemplate, HistoryItem.CONSOLIDATED_CONTEXT, HistoryItem.GLOSSARY);
        var numCtx = FlowSupport.slidingContextWindow(textTokens, history, contextLength);
        var context = AssistantContext.builder()
                .flowContext(flowContext)
                .actionResource(proofreadTemplate)
                .history(history)
                .inputs(Map.of("retryFixString", ""))
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(numCtx);
                    return options;
                })
                .provider(parse(provider))
                .text(text)
                .build();

        var contextResult = worker.work(context, "proofreadEn-%s-".formatted(flowContext.iteration()), Worker.RetryType.SMALL_WITH_FALLBACK, b ->
                retryChunk -> {
                    var chunkContext = b.copy(builder -> builder.text(retryChunk)
                            .input("retryFixString", b.input("retryFixString") + "\n"));
                    var result = requiredAtLeast70Percent(chunkContext, assistant::process);
                    return processAssistantResult(result);
                });
        var result = parseTextFromEntity(contextResult);

        log.info("Proofread text: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::proofreadArg, FlowContextActions.proofread(result));
    }

    /* ============= */

    private AssistantContext.Result processAssistantResult(AssistantContext.Result result) {
        var rawText = result.result();
        var rawXml = CodeBlockExtractor.extract(rawText).trim();
        var mapResult = parseTextFromXml(rawXml, "proofread",
                (id, proofread) -> new OriginText(id, "", proofread));
        if (mapResult.isEmpty()) {
            throw new AppIllegalStateException("All items are empty");
        }
        result.setEntity(mapResult);

        return result;
    }

    private String parseTextFromEntity(AssistantContext.Result context) {
        if (context.entity() instanceof List<?> l && l.getFirst() instanceof OriginText) {
            List<OriginText> list = context.entity();
            return list.stream()
                    .map(OriginText::proofread)
                    .collect(Collectors.joining("\n"));
        }

        return context.result();
    }

    record OriginText(Integer id, String text, String proofread) implements XmlDto {
    }

}
