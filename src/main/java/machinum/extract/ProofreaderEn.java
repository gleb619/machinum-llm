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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProofreaderEn implements ChunkSupport, FlowSupport {

    @Getter
    @Value("${app.proofread.en.model}")
    protected final String chatModel;
    @Value("${app.proofread.en.temperature}")
    protected final Double temperature;
    @Value("${app.proofread.en.numCtx}")
    protected final Integer contextLength;
    @Value("classpath:prompts/custom/system/ProofreadEnSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/ProofreadEn.ST")
    private final Resource proofreadTemplate;
    private final RawInfoTool rawInfoTool;

    private final Assistant assistant;


    public FlowContext<Chapter> proofread(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();

        log.debug("Proofreading story for given: text={}", toShortDescription(text));

        var history = fulfillHistory(systemTemplate, flowContext);

        var context = assistant.process(AssistantContext.builder()
                .flowContext(flowContext)
                .text(text)
                .actionResource(proofreadTemplate)
                .history(history)
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(contextLength);

                    return options;
                })
                .build());

        String result = context.result();

        log.info("Proofread text: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::proofreadArg, FlowContext.proofread(result));
    }

}
