package machinum.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.TypeScope;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.ScoringResult;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.FlowSupport;
import machinum.processor.core.JsonSupport;
import machinum.tool.RawInfoTool;
import machinum.util.CustomTypeReference;
import machinum.util.JavaUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static machinum.config.Constants.SCORE;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractScoring implements FlowSupport, JsonSupport {

    private static final Function<MemberScope, String> DESCRIPTION_RESOLVER = JsonSupport.createComplexDescriptionResolver(ScoringResult.class);

    private static final Function<TypeScope, String> TYPE_DESCRIPTION_RESOLVER = JsonSupport.createTypeDescriptionResolver(ScoringResult.class);

    private static final String USER_TEXT_TEMPLATE = """
            Provide the text of the chapter of the web novel we will be working with
            """;
    @Autowired
    protected RetryHelper retryHelper;
    @Value("classpath:prompts/custom/system/TranslationScoringSystem.ST")
    private Resource systemTemplate;
    @Value("classpath:prompts/custom/TranslationScoring.ST")
    private Resource translationScoringTemplate;
    @Value("${app.translate.scoring.responseLength}")
    private Integer responseLength;
    @Autowired
    @Qualifier("objectMapperHolder")
    private Holder<ObjectMapper> objectMapperHolder;
    @Autowired
    private Assistant assistant;

    @Autowired
    private RawInfoTool rawInfoTool;


    public FlowContext<Chapter> scoreTranslate(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        var translatedText = flowContext.translatedText();
        log.debug("Prepare to score given translation: text={}...", toShortDescription(text));

        var textTokens = countTokens(translatedText);
        var history = prepareHistory(flowContext, text, textTokens);

        var context = doAction(translatedText, history, flowContext.iteration(), textTokens, flowContext);

        log.debug("Prepared score of translated: text={}...", toShortDescription(context.result()));

        return flowContext.rearrange(ctx -> ctx.arg(SCORE), FlowContextActions.createArg(SCORE, context.entity()));
    }

    private List<Message> prepareHistory(FlowContext<Chapter> context, String text, Integer targetLength) {
        List<Message> history;
        //For small models(in context length way), like gemma we're forced to cut the data for history
        if (targetLength > getContextLength()) {
            history = new ArrayList<>();
            history.add(new SystemMessage(systemTemplate));

            context.hasAnyArgument(oldContext -> {
                var contextText = FlowSupport.compressContext(oldContext, targetLength, systemTemplate, getContextLength());

                if (!contextText.isBlank()) {
                    history.add(new UserMessage(USER_PREVIOUS_CONTEXT_TEMPLATE));
                    history.add(new AssistantMessage(contextText));
                }
            }, FlowContext::consolidatedContextArg, FlowContext::contextArg, FlowContext::oldContextArg);
        } else {
            history = fulfillHistory(systemTemplate, context);
            history.add(new UserMessage(USER_TEXT_TEMPLATE));
            history.add(new AssistantMessage(text));
        }

        return history;
    }

    private AssistantContext.Result doAction(String translatedText, List<Message> history, Integer iteration, Integer textTokens, FlowContext<?> flowContext) {
        var contextLength = getContextLength();
        var context = AssistantContext.builder()
                .flowContext(flowContext)
                .operation("%s-%s-".formatted(getOperation(), iteration))
                .text(translatedText)
                .actionResource(translationScoringTemplate)
                .history(history)
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(getTemperature());
                    options.setNumCtx(FlowSupport.slidingContextWindow(textTokens, history, contextLength));

                    return options;
                })
                .outputClass(outputClass())
                .mapper(this::map)
                .build();

        try {
            return retryHelper.withSmallRetry(translatedText, retryChunk -> {
                var resultContext = assistant.process(context.copy(b -> b.text(retryChunk)));

                context.addResultHistory(resultContext.result());
                var entity = parseEntity(resultContext);
                resultContext.setEntity(entity);

                return resultContext;
            });
        } catch (IllegalArgumentException e) {
            var result = context.getMostResultFromHistory();
            return AssistantContext.Result.builder()
                    .result(new StringBuilder(result))
                    .entity(map(result))
                    .build();
        }
    }

    private ScoringResult parseEntity(AssistantContext.Result resultContext) {
        ScoringResult entity = resultContext.entity();
        if (ScoringResult.isEmpty(entity)) {
            entity = map(resultContext.result());
            if (ScoringResult.isEmpty(entity)) {
                log.error("Given entity is empty: {}", entity);
                throw new IllegalArgumentException("Broken result: " + entity);
            }
        }

        return entity;
    }

    @SneakyThrows
    private ScoringResult map(String text) {
        log.error("Given format doesn't fit to expected format: \n\n{}\n", text);

        var objectMapper = objectMapperHolder.data();
        var localText = parse(text);

        if (JavaUtil.isValidJson(localText)) {
            try {
                return (ScoringResult) objectMapper.readValue(localText, CustomTypeReference.of(outputClass()));
            } catch (JsonProcessingException e) {
                //ignore
            }
        }

        throw new IllegalArgumentException("Broken format");
    }

    private ParameterizedTypeReference<ScoringResult> outputClass() {
        return new ParameterizedTypeReference<ScoringResult>() {
        };
    }

    public abstract String getChatModel();

    public abstract Double getTemperature();

    public abstract Integer getContextLength();

    public abstract String getOperation();

}
