package machinum.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.TypeScope;
import machinum.config.Holder;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.flow.FlowSupport;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.processor.core.*;
import machinum.processor.core.AssistantContext.OutputType;
import machinum.tool.RawInfoTool;
import machinum.util.CustomTypeReference;
import machinum.util.JavaUtil;
import machinum.util.TextSearchHelperUtil;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.detectLost;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class AbstractGlossaryTranslate implements JsonSupport, RussianSupport, ObjectNameSupport, FlowSupport {

    public static final Function<MemberScope, String> DESCRIPTION_RESOLVER = JsonSupport.createComplexDescriptionResolver(TranslatedName.class);
    public static final Function<TypeScope, String> TYPE_DESCRIPTION_RESOLVER = JsonSupport.createTypeDescriptionResolver(TranslatedName.class);

    @Getter
    @Value("${app.glossary.translate.model}")
    protected String chatModel;

    @Value("${app.glossary.translate.temperature}")
    protected Double temperature;

    @Value("${app.glossary.translate.numCtx}")
    protected Integer contextLength;
    @Autowired
    protected RetryHelper retryHelper;
    @Autowired
    private Holder<ObjectMapper> objectMapperHolder;
    @Autowired
    private Assistant assistant;
    @Autowired
    private RawInfoTool rawInfoTool;


    public FlowContext<Chapter> translateWithCache(FlowContext<Chapter> flowContext) {
//        var schema = generateSchema(CustomTypeReference.of(outputClass()), DESCRIPTION_RESOLVER, TYPE_DESCRIPTION_RESOLVER);
        var terms = flowContext.glossary().stream()
                .filter(Predicate.not(ObjectName::hasRuName))
                .map(ObjectName::getName)
                .map(s -> s.replace("'", "")
                        .replace("\"", ""))
                .collect(Collectors.joining("\n"));

        return doTranslate(flowContext, terms);
    }

    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var terms = flowContext.glossary().stream()
                .map(ObjectName::getName)
                .map(s -> s.replace("'", "")
                        .replace("\"", ""))
                .collect(Collectors.joining("\n"));

        return doTranslate(flowContext, terms);
    }

    /* ============= */

    public abstract Resource getSystemTemplate();

    public abstract Resource getGlossaryTemplate();

    public abstract OutputType getOutputType();

    /* ============= */

    private FlowContext<Chapter> doTranslate(FlowContext<Chapter> flowContext, String terms) {
        var names = new ArrayList<>(flowContext.glossary());
        var nameMap = names.stream()
                .collect(Collectors.toMap(ObjectName::getName, Function.identity(), (f, s) -> f));
        var text = names.stream()
                .map(ObjectName::stringValue)
                .collect(Collectors.joining("\n"));

        if (terms.isEmpty()) {
            log.debug("All terms was already translated: names={}", names.size());
            return flowContext;
        }

        log.debug("Preparing a translation for extract of given: names={}", names.size());

        var history = fulfillHistory(getSystemTemplate(), flowContext, HistoryItem.NONE);
        var localContext = doAction(flowContext, text, history, terms);

        var newNames = checkAndFixTranslations(flowContext, localContext, nameMap, names);

        return flowContext.replace(FlowContext::glossaryArg, FlowContextActions.glossary(newNames));
    }

    private AssistantContext.Result doAction(FlowContext<Chapter> flowContext, String text, List<Message> history, String terms) {
        var assistantContext = AssistantContext.builder()
                .flowContext(flowContext)
                .operation("glossaryTranslate-%s-".formatted(flowContext.iteration()))
                .text(text)
                .actionResource(getGlossaryTemplate())
                .history(history)
                .inputs(Map.of(
                        "terms", terms
                ))
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setModel(getChatModel());
                    options.setTemperature(temperature);
                    options.setNumCtx(FlowSupport.slidingContextWindow(countTokens(text), history, contextLength));
                    return options;
                })
                .outputType(getOutputType())
                .outputClass(outputClass())
                .mapper(this::map)
                .build();

        var localContext = retryHelper.withRetry(text, retryChunk -> {
            var context = assistant.process(assistantContext.copy(b -> b.text(retryChunk)));

            var result = context.result();
            if (!isRussian(result)) {
                throw new IllegalArgumentException("Lang is not russian: " + result.trim());
            }

            return context;
        });

        return localContext;
    }

    private List<ObjectName> checkAndFixTranslations(FlowContext<Chapter> flowContext, AssistantContext.Result localContext,
                                                     Map<String, ObjectName> nameMap, List<ObjectName> names) {
        List<TranslatedName> translatedNames = localContext.entity();
        var newNames = new HashSet<ObjectName>();

        nameMap.values().forEach(objectName -> {
            if (objectName.hasRuName()) {
                newNames.add(objectName);
            }
        });

        //Set translations into given list
        for (TranslatedName translatedName : translatedNames) {
            var objectName = nameMap.get(translatedName.enName());
            if (Objects.nonNull(objectName)) {
                var newObjectName = objectName.ruName(translatedName.ruName());
                newNames.add(newObjectName);
            }
        }

        //If we have still some names without translation, try to find similar objects, and set translation for them
        if (newNames.size() != names.size()) {
            var namesWithoutTranslation = remap(names, translatedNames, newNames, nameMap);

            if (!namesWithoutTranslation.isEmpty()) {
                //If still have some names without translation, we try to translate again
                var subFlow = translateWithCache(flowContext.replace(FlowContext::glossaryArg, FlowContextActions.glossary(namesWithoutTranslation)));
                var subNamesList = subFlow.glossary();

                for (var subObjectName : subNamesList) {
                    var objectName = nameMap.get(subObjectName.getName());
                    if (Objects.nonNull(objectName)) {
                        var newObjectName = objectName.ruName(subObjectName.ruName());
                        newNames.add(newObjectName);
                    }
                }

            }
        }

        return new ArrayList<>(newNames);
    }

    @SneakyThrows
    private List<TranslatedName> map(String text) {
        log.error("Given format doesn't fit to expected format: \n\n{}\n", text);
//        var schema = generateSchema(CustomTypeReference.of(outputClass()), DESCRIPTION_RESOLVER, TYPE_DESCRIPTION_RESOLVER);

        return switch (getOutputType()) {
            case JSON, STRING -> {
                var objectMapper = objectMapperHolder.data();
                var localText = parse(text);

                if (JavaUtil.isValidJson(localText)) {
                    try {
                        yield (List<TranslatedName>) objectMapper.readValue(localText, CustomTypeReference.of(outputClass()));
                    } catch (JsonProcessingException e) {
                        //ignore
                    }
                }

                throw new IllegalArgumentException("Broken format");
            }
            default -> throw new AppIllegalStateException("Unexpected value: " + getOutputType());
        };
    }

    private List<ObjectName> remap(List<ObjectName> names, List<TranslatedName> translatedNames,
                                   Set<ObjectName> newNames, Map<String, ObjectName> nameMap) {
        var originList = names.stream()
                .map(ObjectName::getName)
                .collect(Collectors.toList());
        var translatedList = translatedNames.stream()
                .map(TranslatedName::enName)
                .collect(Collectors.toList());

        var translation = translatedNames.stream()
                .collect(Collectors.toMap(TranslatedName::enName, TranslatedName::ruName, (f, s) -> f));

        var lostNames = detectLost(originList, translatedList);
        var unsuitableNames = detectLost(translatedList, originList);
        var termsMap = createSuitableMap(lostNames, unsuitableNames);

        for (var entry : termsMap.entrySet()) {
            var objectNameKey = entry.getKey();
            var translatedNameKey = entry.getValue();

            var objectName = nameMap.get(objectNameKey);
            var ruName = translation.get(translatedNameKey);

            var newObjectName = objectName.ruName(ruName);
            newNames.add(newObjectName);
        }

        var result = newNames.size() == names.size();

        if (!result) {
            return newNames.stream()
                    .filter(Predicate.not(ObjectName::hasRuName))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private Map<String, String> createSuitableMap(List<String> originList, List<String> translatedList) {
        var lostNames = detectLost(originList, translatedList);
        var unsuitableNames = new ArrayList<>(detectLost(translatedList, originList));
        var resultMap = new HashMap<String, String>();

        for (var lostName : lostNames) {
            var possibleObjects = TextSearchHelperUtil.search(unsuitableNames, lostName);

            if (!possibleObjects.isEmpty()) {
                var bestMatch = possibleObjects.getFirst();
                resultMap.put(lostName, bestMatch);
                unsuitableNames.remove(bestMatch);
            }
        }

        return resultMap;
    }

    private ParameterizedTypeReference<List<TranslatedName>> outputClass() {
        return new ParameterizedTypeReference<List<TranslatedName>>() {
        };
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    @JsonDescription("A glossary term.")
    public static class Translations {

        @JsonDescription("A collection of translation pairs.")
        private List<TranslatedName> translations;

    }

    @JsonDescription("A translation pair.")
    public record TranslatedName(
            @NotNull
            @NotEmpty
            @JsonDescription("Name in English.")
            String enName,

            @NotNull
            @NotEmpty
            @JsonDescription("Name in Russian.")
            String ruName) implements StringSupport {

        public static TranslatedName of(String key, String value) {
            return new TranslatedName(key, value);
        }

        @Override
        public String stringValue() {
            return "`%s` - it's a `%s` on Russian;".formatted(enName, ruName);
        }

    }

}
