package machinum.extract;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.flow.Pack;
import machinum.model.Chapter;
import machinum.processor.core.*;
import machinum.tool.RawInfoTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static machinum.config.Constants.TITLE;
import static machinum.config.Constants.TRANSLATED_TITLE;
import static machinum.util.JavaUtil.createSuitableMap;
import static machinum.util.TextUtil.*;

@Slf4j
public abstract class AbstractTranslaterHeader implements FlowSupport, PreconditionSupport, JsonSupport, PropertiesSupport {

    private static final String USER_PREVIOUS_CHAPTER_TITLE_TEMPLATE = """
            Provide translation for previous web novel's chapter title:\s
            %s
            """;

    @Getter
    @Value("${app.translate.title.model}")
    protected String chatModel;
    @Value("${app.translate.title.temperature}")
    protected Double temperature;
    @Value("${app.translate.title.numCtx}")
    protected Integer contextLength;

    @Autowired
    private Assistant assistant;

    @Autowired
    private RawInfoTool rawInfoTool;

    @Autowired
    private RetryHelper retryHelper;


    public abstract Resource getSystemTemplate();

    public abstract Resource getTranslateTemplate();

    public FlowContext<Chapter> batchTranslate(FlowContext<Chapter> flowContext) {
        List<Pack<Chapter, String>> items = flowContext.result();
        var data = items.stream()
                .collect(Collectors.toMap(pack -> pack.getArgument().getValue(), Pack::getItem, (f, s) -> f, LinkedHashMap::new));

        var text = String.join("\n", data.keySet());

        log.debug("Prepare to translate batch: titles[{}]={}...", data.size(), toShortDescription(data.keySet()));

        var aiResult = doTranslate(flowContext, text, false);
        var result = remap(data, aiResult.entity());

        var output = result.entrySet().stream()
                .map(entry -> Pack.createNew(b -> b
                        .item(data.get(entry.getKey()))
                        .argument(FlowContextActions.createArg(TRANSLATED_TITLE, entry.getValue()))
                ))
                .filter(Predicate.not(Pack::isEmpty))
                .collect(Collectors.toList());

        log.debug("Prepared translated version: titles[{}]=\n{}...", result.size(), indent(toShortDescription(result.values())));

        if (output.size() != data.size()) {
            throw new AppIllegalStateException("Some titles is not translated: %s <> %s", data.size(), output.size());
        }

        return flowContext.rearrange(FlowContext::resultArg, FlowContextActions.result(output));
    }

    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var titleArg = flowContext.arg(TITLE);
        var text = titleArg.stringValue();
        log.debug("Prepare to translate: title={}...", toShortDescription(text));

        var result = doTranslate(flowContext, text, true)
                .result();

        log.debug("Prepared translated version: title={}...", toShortDescription(result));

        return flowContext.rearrange(ctx -> ctx.arg(TRANSLATED_TITLE), FlowContextActions.createArg(TRANSLATED_TITLE, result));
    }

    /* ============= */

    private AssistantContext.Result doTranslate(FlowContext<Chapter> flowContext, String text, boolean mode) {
        //TODO use NLP extract names from title, go to glossary and check if we already have such term
        var history = fulfillHistory(getSystemTemplate(), flowContext, HistoryItem.CONSOLIDATED_GLOSSARY, HistoryItem.GLOSSARY);
        flowContext.hasArgument(ctx -> ctx.arg(TRANSLATED_TITLE), translatedTitle -> {
            var previousTitle = flowContext.oldArg(TITLE);

            history.add(new UserMessage(USER_PREVIOUS_CHAPTER_TITLE_TEMPLATE.formatted(previousTitle.stringValue())));
            history.add(new AssistantMessage(translatedTitle.stringValue()));
        });

        return doAction(flowContext, text, history, mode);
    }

    private AssistantContext.Result doAction(FlowContext<Chapter> flowContext, String text, List<Message> history,
                                             boolean simpleMode) {
        var context = AssistantContext.builder()
                .flowContext(flowContext)
                .operation("translateTitle-%s-".formatted(flowContext.iteration()))
                .text(text)
                .actionResource(getTranslateTemplate())
                .history(history)
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(contextLength);
                    return options;
                })
                .build();

        if (simpleMode) {
            return assistant.process(context);
        } else {
            return retryHelper.withSmallRetry(text, retryChunk -> {
                var contextResult = requiredNotEmpty(context.copy(b -> b.text(retryChunk)), assistant::process);
                var mapResult = parsePropertiesFromMarkdown(contextResult.result());
                if (mapResult.isEmpty()) {
                    throw new AppIllegalStateException("All titles are empty");
                }
                contextResult.setEntity(mapResult);

                return contextResult;
            });
        }
    }

    private Map<String, String> remap(Map<String, Chapter> originTitles, Map<String, String> translatedTitles) {
        var output = new HashMap<String, String>();
        var originList = originTitles.keySet();
        var translatedList = translatedTitles.keySet();

        var lostNames = detectLost(originList, translatedList);
        var unsuitableNames = detectLost(translatedList, originList);
        var possibleNames = createSuitableMap(lostNames, unsuitableNames);

        for (var key : originTitles.keySet()) {
            output.put(key, translatedTitles.get(key));
        }

        for (var entry : possibleNames.entrySet()) {
            var originKey = entry.getKey();
            var translatedKey = entry.getValue();

            output.put(originKey, translatedTitles.get(translatedKey));
        }

        if (originTitles.size() != output.size()) {
            throw new AppIllegalStateException("Some of the titles were not found: \n\torigin: %s\n\ttranslated: %s\n", originTitles.keySet(), translatedTitles.values());
        }

        return output;
    }

}
