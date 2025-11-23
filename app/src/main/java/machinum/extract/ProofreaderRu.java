package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.AppFlowActions;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.ChunkSupport;
import machinum.processor.core.FlowSupport;
import machinum.tool.RawInfoTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static machinum.extract.Worker.RetryType.FULL;
import static machinum.processor.core.FlowSupport.HistoryItem.CONTEXT;
import static machinum.processor.core.FlowSupport.HistoryItem.NONE;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProofreaderRu implements ChunkSupport, FlowSupport {

    private static final String USER_GLOSSARY_TEMPLATE = "Use this glossary information:";

    @Getter
    @Value("${app.proofread.ru.model}")
    protected final String chatModel;
    @Value("${app.proofread.ru.temperature}")
    protected final Double temperature;
    @Value("${app.proofread.ru.numCtx}")
    protected final Integer contextLength;
    private final Worker worker;
    @Value("classpath:prompts/transform/system/ProofreadRuSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/transform/ProofreadRu.ST")
    private final Resource proofreadTemplate;
    private final RawInfoTool rawInfoTool;

    public FlowContext<Chapter> proofread(FlowContext<Chapter> flowContext) {
        var text = flowContext.translatedText();

        log.debug("Proofreading story for given: text={}", toShortDescription(text));

        var textTokens = countTokens(text);
        var history = prepareHistory(flowContext, textTokens);
        var subIteration = flowContext.optionalValue(FlowContext::subIterationArg).orElse(0);

        var result = doAction(flowContext, text, history, flowContext.iteration(), subIteration, textTokens);

        log.info("Proofread text: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::translatedTextArg, FlowContextActions.translatedText(result));
    }

    private String doAction(FlowContext<Chapter> flowContext, String text, List<Message> history, Integer iteration,
                            Integer subIteration, Integer textTokens) {
        var assistantContext = AssistantContext.builder()
                .operation("proofread-%s-%s-".formatted(iteration, subIteration))
                .flowContext(flowContext)
                .text(text)
                .actionResource(proofreadTemplate)
                .history(history)
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(textTokens, history, contextLength));

                    return options;
                })
                .build();

        var context = worker.work(assistantContext, "proofread-%s-%s-".formatted(iteration, subIteration), null, FULL);

        return context.result();
    }

    private List<Message> prepareHistory(FlowContext<Chapter> flowContext, Integer textTokens) {
        int targetLength = textTokens * 2;
        var historySettings = new ArrayList<HistoryItem>();

        //If we have space, then we try to add base context
        if (calculatePercent(targetLength, contextLength) <= 70) {
            historySettings.add(CONTEXT);
        } else {
            historySettings.add(NONE);
        }

        List<Message> history = new ArrayList<>(fulfillHistory(systemTemplate, flowContext, historySettings));
        flowContext.hasArgument(AppFlowActions::glossaryArg, glossary -> {
            var value = glossary.getValue().stream()
                    .map(ObjectName::invertedStringValue)
                    .collect(Collectors.joining("\n"));

            history.add(new UserMessage(USER_GLOSSARY_TEMPLATE));
            history.add(new AssistantMessage(value));
        });

        return history;
    }

}
