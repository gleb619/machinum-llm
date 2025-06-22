package machinum.extract;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.FlowSupport;
import machinum.processor.core.PreconditionSupport;
import machinum.tool.RawInfoTool;
import machinum.util.TextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static machinum.config.Constants.SCORE;
import static machinum.processor.core.AiClient.Provider.parse;
import static machinum.processor.core.FlowSupport.HistoryItem.*;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
public abstract class AbstractTranslaterBody implements FlowSupport, PreconditionSupport {

    protected static final String USER_TEXT_TEMPLATE = """
            Provide Russian translation for next web novel's chapter text:
            %s
            """;

    protected static final String USER_TRANSLATE_TEMPLATE = """
            Provide the translated text of the chapter of the web novel we will be working with 
            """;

    protected static final String USER_CHUNK_TEMPLATE = """
            Provide last lines for the previous web novel's chapter chunk text""";

    protected static final String USER_TRANSLATED_CHUNK_TEMPLATE = """
            Provide Russian translation for the web novel's chapter chunk text:
            %s 
            """;

    protected static final String USER_SCORE_TRANSLATE_TEMPLATE = """
            Provide a report with scoring to improve translation
            """;

    protected static final String CONTINUE_TRANSLATION_TEMPLATE = """
            Your Task:
            Correct the translation for previous web novel's chapter according to the report with scoring for improvement of translation.
            Do not add or remove paragraphs; keep the exact same structure.
            Do not add any explain, notes, examples or thoughts, just provide the corrected version of translated text:
            {retryFixString}
            """;

    @Getter
    @Value("${app.translate.model}")
    protected String chatModel;
    @Value("${app.translate.temperature}")
    protected Double temperature;
    @Value("${app.translate.numCtx}")
    protected Integer contextLength;
    @Value("${app.translate.provider}")
    protected String provider;

    @Autowired
    protected Assistant assistant;
    @Autowired
    protected RawInfoTool rawInfoTool;
    @Autowired
    protected RetryHelper retryHelper;


    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        log.debug("Prepare to translate: text={}...", toShortDescription(text));

        var textTokens = countTokens(text);
        var hasScoring = new AtomicBoolean(false);
        var history = prepareHistory(flowContext, textTokens, text, hasScoring);

        var subIteration = flowContext.optionalValue(FlowContext::subIterationArg).orElse(0);
        var resource = parseResource(hasScoring);
        var context = doAction(text, resource, history, flowContext.iteration(), subIteration, hasScoring.get(), textTokens);

        var translatedText = parseTranslatedText(context);
        log.debug("Prepared translated version: text={}...", toShortDescription(translatedText));

        return flowContext.rearrange(FlowContext::translatedTextArg, FlowContextActions.translatedText(translatedText));
    }

    /* ============= */

    public abstract Resource getSystemTemplate();

    public abstract Resource getTranslateTemplate();

    protected String parseTranslatedText(AssistantContext.Result context) {
        return context.result();
    }

    protected AssistantContext.Result processAssistantResult(AssistantContext.Result result) {
        return result;
    }

    /* ============= */

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

        var history = fulfillHistory(getSystemTemplate(), flowContext, historySettings);

        flowContext.hasArgument(FlowContext::oldChunkArg, chunkArg -> {
            flowContext.hasArgument(FlowContext::translatedChunkArg, translatedChunkArg -> {
                //If previous chunk is more that 5% of context window we truncate head of text, and leave only last 5 sentences/lines
                history.add(new UserMessage(USER_CHUNK_TEMPLATE));

                history.add(new AssistantMessage(chunkArg
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
                .provider(parse(provider))
                .build();

        try {
            return retryHelper.withSmallRetry(text, retryChunk -> {
                var result = requiredAtLeast70Percent(context.copy(b -> b.text(retryChunk)
                        .input("retryFixString", context.input("retryFixString") + "\n")
                ), assistant::process);

                return processAssistantResult(result);
            });
        } catch (LengthValidationException e) {
            var result = context.getMostResultFromHistory();
            return AssistantContext.Result.of(result);
        }
    }

    private Resource parseResource(AtomicBoolean hasScoring) {
        return hasScoring.get() ? new ByteArrayResource(CONTINUE_TRANSLATION_TEMPLATE.getBytes(StandardCharsets.UTF_8)) : getTranslateTemplate();
    }

}
