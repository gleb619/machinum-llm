package machinum.extract;

import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.flow.FlowContext;
import machinum.flow.FlowSupport;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.tool.RawInfoTool;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static machinum.config.Constants.TITLE;
import static machinum.config.Constants.TRANSLATED_TITLE;
import static machinum.util.TextUtil.toShortDescription;
import static machinum.util.TextUtil.valueOf;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslaterHeader implements FlowSupport {

    private static final String USER_PREVIOUS_CHAPTER_TITLE_TEMPLATE = """
            Provide translation for previous web novel's chapter title:\s
            %s
            """;
    @Getter
    @Value("${app.translate.title.model}")
    protected final String chatModel;
    @Value("${app.translate.title.temperature}")
    protected final Double temperature;
    @Value("${app.translate.title.numCtx}")
    protected final Integer contextLength;
    @Value("classpath:prompts/custom/system/TranslateHeaderSystem.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/TranslateHeader.ST")
    private final Resource translateTemplate;
    private final Assistant assistant;

    private final RawInfoTool rawInfoTool;


    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var titleArg = flowContext.arg(TITLE);
        var text = titleArg.stringValue();
        log.debug("Prepare to translate: title={}...", toShortDescription(text));

        //TODO use NLP extract names from title, go to glossary and check if we already have such term
        var history = fulfillHistory(systemTemplate, flowContext, HistoryItem.GLOSSARY);
        flowContext.hasArgument(ctx -> ctx.arg(TRANSLATED_TITLE), translatedTitle -> {
            var previousTitle = flowContext.oldArg(TITLE);

            history.add(new UserMessage(USER_PREVIOUS_CHAPTER_TITLE_TEMPLATE.formatted(previousTitle.stringValue())));
            history.add(new AssistantMessage(translatedTitle.stringValue()));
        });

        var context = assistant.process(AssistantContext.builder()
                .flowContext(flowContext)
                .operation("translateTitle-%s-".formatted(flowContext.iteration()))
                .text(text)
                .actionResource(translateTemplate)
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

        log.debug("Prepared translated version: title={}...", toShortDescription(result));

        return flowContext.rearrange(ctx -> ctx.arg(TRANSLATED_TITLE), FlowContextActions.createArg(TRANSLATED_TITLE, result));
    }

}
