package machinum.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.flow.AppFlowActions;
import machinum.flow.model.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.processor.client.AiClient;
import machinum.processor.core.*;
import machinum.tool.RawInfoTool;
import machinum.util.CustomTypeReference;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static machinum.config.Constants.FLOW_TYPE;
import static machinum.processor.HistoryService.TokenBudget.from;
import static machinum.processor.core.FlowSupport.HistoryItem.*;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlossaryExtractor implements JsonSupport, ObjectNameSupport, ChunkSupport, FlowSupport {

    public static final int POSSIBLE_RESPONSE_SIZE = 1_000;

    @Value("${app.split.overlap-size}")
    protected final Integer overlapSize;
    @Getter
    @Value("${app.glossary.extract.model}")
    protected final String chatModel;
    @Value("${app.glossary.extract.temperature}")
    protected final Double temperature;
    @Value("${app.glossary.extract.numCtx}")
    protected final Integer contextLength;
    @Value("${app.glossary.extract.provider}")
    protected final String provider;
    protected final RetryHelper retryHelper;
    @Value("classpath:prompts/custom/system/GlossaryExtractorFirstSystem.ST")
    private final Resource firstSystemTemplate;
    @Value("classpath:prompts/custom/system/GlossaryExtractorSecondSystem.ST")
    private final Resource secondSystemTemplate;
    @Value("classpath:prompts/custom/GlossaryExtractor.json.ST")
    private final Resource glossaryTemplate;
    private final RawInfoTool rawInfoTool;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;
    private final Assistant assistant;
    private final Worker worker;

    public FlowContext<Chapter> firstExtract(FlowContext<Chapter> flowContext) {
        return doAction("glossaryExtract", flowContext, firstSystemTemplate, true);
    }

    public FlowContext<Chapter> secondExtract(FlowContext<Chapter> flowContext) {
        boolean isSimpleFlow = "simple".equals(flowContext.metadata(FLOW_TYPE, "none"));

        return doAction("glossaryEnrich", flowContext, secondSystemTemplate, !isSimpleFlow);
    }

    private FlowContext<Chapter> doAction(String name, FlowContext<Chapter> flowContext, Resource sysTemplate, boolean createHistory) {
        var text = flowContext.text();
        var textTokens = countTokens(text);
        List<Message> history;
        if (createHistory) {
            int targetLength = textTokens * 2;
            var historySettings = new ArrayList<HistoryItem>();
            if (calculatePercent(targetLength, contextLength) <= 70) {
                historySettings.add(GLOSSARY);
                historySettings.add(CONSOLIDATED_GLOSSARY);
            }
            if (calculatePercent(targetLength, contextLength) <= 60) {
                historySettings.add(CONTEXT);
                historySettings.add(CONSOLIDATED_CONTEXT);
            }
            history = worker.createCustomHistory(flowContext, sysTemplate, historySettings.toArray(new HistoryItem[0]));
        } else {
            history = worker.createAdvancedHistory(HistoryContext.builder()
                    .systemMessage(sysTemplate)
                    .flowContext(flowContext)
                    .budget(from(contextLength).allocate(POSSIBLE_RESPONSE_SIZE).allocate(textTokens))
                    .allowedItems(List.of(CONSOLIDATED_GLOSSARY, GLOSSARY))
                    .build());
        }

        log.debug("Preparing a extract names for given: text={}...", toShortDescription(text));

        var numCtx = worker.getSlidingWindow(textTokens, history, contextLength);
        var context = AssistantContext.builder()
                .flowContext(flowContext)
                .actionResource(glossaryTemplate)
                .history(history)
                .tools(List.of(rawInfoTool))
                .customizeChatOptions(options -> {
                    options.setNumCtx(numCtx);
                    return options;
                })
                .provider(AiClient.Provider.parse(provider))
                .outputClass(outputClass())
                .mapper(this::map)
                .outputType(AssistantContext.OutputType.JSON)
                .text(text)
                .build();

        var contextResult = worker.work(context, name, null, Worker.RetryType.FULL);
        List<ObjectName> output = contextResult.entity();

        var names = output.stream()
                .sorted(Comparator.comparing(ObjectName::getName))
                .collect(Collectors.toList());

        log.debug("Created glossary terms[{}]: {}", output.size(), output.stream()
                .map(ObjectName::getName)
                .map("`%s`"::formatted)
                .collect(Collectors.joining(";")));

        return flowContext.rearrange(AppFlowActions::glossaryArg, AppFlowActions.glossary(names));
    }

    @SneakyThrows
    private Object map(String rawText) {
        log.error("Given format doesn't fit to expected format: \n\n{}\n", rawText);

        var objectMapper = objectMapperHolder.data();
        var text = parse(rawText);

        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            jsonNode = objectMapper.readTree(text.replaceAll("\"(\\w+): \"", "\"$1\": \""));
        }

        if (jsonNode.isArray()) {
            var reader = objectMapper.readerFor(CustomTypeReference.of(outputClass()));
            return reader.readValue(jsonNode);
        } else if (jsonNode.has("items")) {
            var items = jsonNode.get("items");
            if (items.isArray()) {
                var reader = objectMapper.readerFor(CustomTypeReference.of(outputClass()));
                return reader.readValue(items);
            }
        }

        throw new IllegalArgumentException("Broken format");
    }

    private ParameterizedTypeReference<List<ObjectName>> outputClass() {
        return new ParameterizedTypeReference<List<ObjectName>>() {
        };
    }

}
