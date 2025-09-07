package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static machinum.config.Constants.FLOW_TYPE;
import static machinum.config.Constants.SCORE;
import static machinum.processor.client.AiClient.Provider.parse;
import static machinum.processor.core.FlowSupport.HistoryItem.*;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrammarEditor implements FlowSupport, PreconditionSupport {

    private static final String USER_TEXT_TEMPLATE = """
            Provide the English version of text of the chapter of the web novel we will be working with 
            """;

    private static final String USER_TRANSLATE_TEMPLATE = """
            Provide the translated text of the chapter of the web novel we will be working with 
            """;

    private static final String USER_CHUNK_TEMPLATE = """
            Provide copy edited Russian translation for the web novel's chapter chunk text:
            %s 
            """;

    private static final String USER_SCORE_TRANSLATE_TEMPLATE = """
            Provide a report with scoring to improve translation
            """;

    private static final String CONTINUE_COPY_EDIT_TEMPLATE = """
            Your Task:
            Correct the translation for web novel's chapter text according to the report with scoring for improvement of translation.
            Do not add or remove paragraphs; keep the exact same structure.
            Do not add any explain, notes, examples or thoughts, just provide the corrected version of translated text:
            """;
    @Getter
    @Value("${app.translate.copy-editing.model}")
    protected final String chatModel;
    @Value("${app.translate.copy-editing.temperature}")
    protected final Double temperature;
    @Value("${app.translate.copy-editing.numCtx}")
    protected final Integer contextLength;
    @Value("${app.translate.copy-editing.provider}")
    protected final String provider;
    //TODO remove
    @Value("${app.translate.copy-editing.history.mode}")
    protected final String historyMode;
    protected final RetryHelper retryHelper;
    @Value("classpath:prompts/custom/system/GrammarEditorSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/GrammarEditor.ST")
    private final Resource copyEditingTemplate;
    private final Assistant assistant;

    private final RawInfoTool rawInfoTool;

    public FlowContext<Chapter> fixTranslate(FlowContext<Chapter> flowContext) {
        boolean isSimpleFlow = "simple".equals(flowContext.metadata(FLOW_TYPE, "none"));
        return doFixTranslate(flowContext, !isSimpleFlow);
    }

    private FlowContext<Chapter> doFixTranslate(FlowContext<Chapter> flowContext, boolean createHistory) {
        var text = flowContext.text();
        var translatedText = flowContext.translatedText();
        var translatedTextTokens = countTokens(translatedText);
        log.debug("Prepare to copy edit translation: text={}...", toShortDescription(text));
        var hasScoring = new AtomicBoolean(false);

        List<Message> history;
        //TODO redo to only existed entries, for provider
        if (createHistory) {
            history = prepareHistory(flowContext, text, hasScoring, translatedTextTokens);
        } else {
            history = List.of(new SystemMessage(systemTemplate));
        }

        var resource = parseResource(hasScoring);
        var context = doAction(translatedText, history, resource, flowContext.iteration(), hasScoring.get(),
                translatedTextTokens, flowContext);

        String result = context.result();

        log.debug("Prepared copy edit translation version: text={}...", toShortDescription(result));

        return flowContext.rearrange(FlowContext::translatedTextArg, FlowContextActions.translatedText(result));
    }

    private Resource parseResource(AtomicBoolean hasScoring) {
        return hasScoring.get() ? new ByteArrayResource(CONTINUE_COPY_EDIT_TEMPLATE.getBytes(StandardCharsets.UTF_8)) : copyEditingTemplate;
    }

    private List<Message> prepareHistory(FlowContext<Chapter> flowContext, String text, AtomicBoolean hasScoring,
                                         Integer translatedTextTokens) {

        List<Message> history;

        if ("auto".equals(historyMode)) {
            history = prepareHistoryOld(flowContext, text, hasScoring, translatedTextTokens);
        } else if ("min".equals(historyMode)) {
            history = fulfillHistory(systemTemplate, flowContext, GLOSSARY);
        } else {
            throw new IllegalArgumentException("Unknown history mode: " + historyMode);
        }

        flowContext.hasArgumentOr(ctx -> ctx.arg(SCORE), arg -> {
            flowContext.hasArgument(FlowContext::translatedTextArg, translatedTextArg -> {
                history.add(new UserMessage(USER_TEXT_TEMPLATE));
                history.add(new AssistantMessage(text));

                history.add(new UserMessage(USER_TRANSLATE_TEMPLATE));
                history.add(new AssistantMessage(translatedTextArg.stringValue()));

                history.add(new UserMessage(USER_SCORE_TRANSLATE_TEMPLATE));
                history.add(new AssistantMessage(arg.stringValue()));

                hasScoring.set(true);
            });
        }, ctx -> {
            history.add(new UserMessage(USER_TEXT_TEMPLATE));
            history.add(new AssistantMessage(text));
        });

        return history;
    }

    private List<Message> prepareHistoryOld(FlowContext<Chapter> flowContext, String text, AtomicBoolean hasScoring, Integer translatedTextTokens) {
        var historySettings = new ArrayList<HistoryItem>();
        var hasScore = flowContext.hasArgument(ctx -> ctx.arg(SCORE));
        int step = hasScore ? 20 : 10;

        //If we have space, then we try to add base context
        if (calculatePercent(translatedTextTokens * 2, contextLength) <= step + 10) {
            historySettings.add(CONTEXT);
            historySettings.add(GLOSSARY);
        }

        //If we have space, then we try to add extended context
        if (!hasScore && calculatePercent(translatedTextTokens * 2, contextLength) <= step) {
            historySettings.add(CONSOLIDATED_CONTEXT);
            historySettings.add(CONSOLIDATED_GLOSSARY);
        }

        var history = fulfillHistory(systemTemplate, flowContext, historySettings);

        flowContext.hasArgument(FlowContext::oldChunkArg, chunkArg -> {
            flowContext.hasArgument(FlowContext::oldTranslatedTextArg, translatedTextArg -> {
                //If previous chunk is more that 5% of context window we truncate head of text, and leave only last 5 sentences/lines
                history.add(new UserMessage(USER_CHUNK_TEMPLATE.formatted(chunkArg
                        .mapValueWithCondition(chunk -> chunk.check(s -> calculatePercent(countTokens(s), contextLength) >= 5),
                                chunk -> chunk.map(TextUtil::truncateFromHead))
                        .stringValue())));
                history.add(new AssistantMessage(translatedTextArg
                        .mapValueWithCondition(s -> calculatePercent(countTokens(s), contextLength) >= 5,
                                TextUtil::truncateFromHead)
                        .stringValue()));
            });
        });

        return history;
    }

    private AssistantContext.Result doAction(String translatedText, List<Message> history, Resource actionResource,
                                             Integer iteration, boolean hasScoring, Integer translatedTextTokens,
                                             FlowContext<Chapter> flowContext) {
        var context = AssistantContext.builder()
                .flowContext(flowContext)
                .operation("copyEdit-%s-".formatted(iteration))
                .text(translatedText)
                .actionResource(actionResource)
                .history(history)
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(hasScoring ? (int) (translatedTextTokens * 1.2) : translatedTextTokens, history, contextLength));
                    return options;
                })
                .provider(parse(provider))
                .build();

        try {
            return retryHelper.withSmallRetry(translatedText, retryChunk ->
                    requiredAtLeast80Percent(
                            context.copy(b -> b.text(retryChunk)), assistant::process));
        } catch (LengthValidationException e) {
            var result = context.getMostResultFromHistory();
            return AssistantContext.Result.of(result);
        }
    }

}
