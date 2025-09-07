package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.ChunkSupport;
import machinum.processor.core.FlowSupport;
import machinum.ssml.SSMLParser;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.util.List;

import static machinum.processor.core.FlowSupport.HistoryItem.NONE;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
public class SSMLSerializer implements ChunkSupport, FlowSupport {

    @Getter
    @Value("${app.ssml.model}")
    protected final String chatModel;
    @Value("${app.ssml.temperature}")
    protected final Double temperature;
    @Value("${app.ssml.numCtx}")
    protected final Integer contextLength;
    protected final RetryHelper retryHelper;
    @Value("classpath:prompts/transform/system/SSMLSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/transform/SSML.ST")
    private final Resource ssmlTemplate;
    private final SSMLParser parser = new SSMLParser();
    private final Assistant assistant;

    public FlowContext<Chapter> convert(FlowContext<Chapter> flowContext) {
        var text = flowContext.translatedText();

        log.debug("Converting text to SSML format: text={}", toShortDescription(text));

        var textTokens = countTokens(text);
        var history = fulfillHistory(systemTemplate, flowContext, NONE);
        var subIteration = flowContext.optionalValue(FlowContext::subIterationArg).orElse(0);

        var result = doAction(flowContext, text, history, flowContext.iteration(), subIteration, textTokens);
        var ssml = parser.repair(result);

        log.info("SSML text: text={}...", toShortDescription(ssml));

        return flowContext.rearrange(FlowContext::resultArg, FlowContextActions.result(ssml));
    }

    private String doAction(FlowContext<Chapter> flowContext, String text, List<Message> history, Integer iteration,
                            Integer subIteration, Integer textTokens) {
        var assistantContext = AssistantContext.builder()
                .operation("proofread-%s-%s-".formatted(iteration, subIteration))
                .flowContext(flowContext)
                .text(text)
                .actionResource(ssmlTemplate)
                .history(history)
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

}
