package machinum.extract;

import machinum.model.Chapter;
import machinum.flow.FlowContext;
import machinum.flow.FlowSupport;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.PreconditionSupport;
import machinum.tool.RawInfoTool;
import machinum.util.TextUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static machinum.config.Constants.SCORE;
import static machinum.flow.FlowSupport.HistoryItem.*;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslaterBody implements FlowSupport, PreconditionSupport {

    private static final String USER_TEXT_TEMPLATE = """
            Provide Russian translation for next web novel's chapter text:
            %s
            """;

    private static final String USER_TRANSLATE_TEMPLATE = """
            Provide the translated text of the chapter of the web novel we will be working with 
            """;

    private static final String USER_CHUNK_TEMPLATE = """
            Provide Russian translation for the web novel's chapter chunk text:
            %s 
            """;

    private static final String USER_SCORE_TRANSLATE_TEMPLATE = """
            Provide a report with scoring to improve translation
            """;

    private static final String CONTINUE_TRANSLATION_TEMPLATE = """
            Your Task:
            Correct the translation for previous web novel's chapter according to the report with scoring for improvement of translation.
            Do not add or remove paragraphs; keep the exact same structure.
            Do not add any explain, notes, examples or thoughts, just provide the corrected version of translated text:
            {retryFixString}
            """;
    @Getter
    @Value("${app.translate.model}")
    protected final String chatModel;
    @Value("${app.translate.temperature}")
    protected final Double temperature;
    @Value("${app.translate.numCtx}")
    protected final Integer contextLength;
    @Value("classpath:prompts/custom/system/TranslateBodySystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/TranslateBody.ST")
    private final Resource translateTemplate;
    private final Assistant assistant;

    private final RawInfoTool rawInfoTool;

    private final RetryHelper retryHelper;


    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        log.debug("Prepare to translate: text={}...", toShortDescription(text));

        var textTokens = countTokens(text);
        var hasScoring = new AtomicBoolean(false);
        var history = prepareHistory(flowContext, textTokens, text, hasScoring);

        var subIteration = flowContext.optionalValue(FlowContext::subIterationArg).orElse(0);
        var resource = parseResource(hasScoring);
        var context = doAction(text, resource, history, flowContext.iteration(), subIteration, hasScoring.get(), textTokens);

        log.debug("Prepared translated version: text={}...", toShortDescription(context.result()));

        return flowContext.rearrange(FlowContext::translatedTextArg, FlowContext.translatedText(context.result()));
    }

    private List<Message> prepareHistory(FlowContext<Chapter> flowContext, Integer textTokens, String text, AtomicBoolean hasScoring) {
        var historySettings = new ArrayList<HistoryItem>();
        var hasScore = flowContext.hasArgument(ctx -> ctx.arg(SCORE));
        int step = hasScore ? 20 : 10;

        //If we have space, then we try to add base context
        if (calculatePercent(textTokens * 2, contextLength) <= step + 10) {
            historySettings.add(CONTEXT);
            historySettings.add(GLOSSARY);
        }

        //If we have space, then we try to add extended context
        if (!hasScore && calculatePercent(textTokens * 2, contextLength) <= step) {
            historySettings.add(CONSOLIDATED_CONTEXT);
            historySettings.add(CONSOLIDATED_GLOSSARY);
        }

        var history = fulfillHistory(systemTemplate, flowContext, historySettings);

        flowContext.hasArgument(FlowContext::oldChunkArg, chunkArg -> {
            flowContext.hasArgument(FlowContext::translatedChunkArg, translatedChunkArg -> {
                //If previous chunk is more that 5% of context window we truncate head of text, and leave only last 5 sentences/lines
                history.add(new UserMessage(USER_CHUNK_TEMPLATE.formatted(chunkArg
                        .mapValueWithCondition(chunk -> chunk.check(s -> calculatePercent(countTokens(s), contextLength) >= 5),
                                chunk -> chunk.map(TextUtil::truncateFromHead))
                        .stringValue())));

                history.add(new AssistantMessage(translatedChunkArg
                        .mapValueWithCondition(chunk -> chunk.check(s -> calculatePercent(countTokens(s), contextLength) >= 5),
                                chunk -> chunk.map(TextUtil::truncateFromHead))
                        .stringValue()));
            });
        });

        flowContext.hasArgument(ctx -> ctx.arg(SCORE), scoreArg -> {
            flowContext.hasArgument(FlowContext::translatedTextArg, translatedTextArg -> {
                history.add(new UserMessage(USER_TEXT_TEMPLATE.formatted(text)));
                history.add(new AssistantMessage(translatedTextArg.stringValue()));

                history.add(new UserMessage(USER_SCORE_TRANSLATE_TEMPLATE));
                history.add(new AssistantMessage(scoreArg.stringValue()));

                hasScoring.set(true);
            });
        });

        return history;
    }

    private AssistantContext.Result doAction(String text, Resource resource, List<Message> history, Integer iteration,
                                             Integer subIteration, boolean hasScoring, Integer textTokens) {
        var context = AssistantContext.builder()
                .operation("translateText-%s-%s-".formatted(iteration, subIteration))
                .text(text)
                .actionResource(resource)
                .history(history)
                .inputs(Map.of(
                        "retryFixString", ""
                ))
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(hasScoring ? (int) (textTokens * 1.2) : textTokens, history, contextLength));
                    return options;
                })
                .build();

        try {
            return retryHelper.withSmallRetry(text, retryChunk ->
                    requiredAtLeast70Percent(context.copy(b -> b.text(retryChunk)
                            .input("retryFixString", context.input("retryFixString") + "\n")
                    ), assistant::process));
        } catch (LengthValidationException e) {
            var result = context.getMostResultFromHistory();
            return AssistantContext.Result.of(result);
        }
    }

    private Resource parseResource(AtomicBoolean hasScoring) {
        return hasScoring.get() ? new ByteArrayResource(CONTINUE_TRANSLATION_TEMPLATE.getBytes(StandardCharsets.UTF_8)) : translateTemplate;
    }

}
