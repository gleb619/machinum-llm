package machinum.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.TypeScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.flow.core.FlowContext;
import machinum.model.ChainOfThoughts;
import machinum.model.Chapter;
import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.JsonSupport;
import machinum.processor.core.SplitFactory;
import machinum.tool.RawInfoTool;
import machinum.util.CustomTypeReference;
import machinum.util.TextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.retry.RetryHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static machinum.processor.core.SplitFactory.MAX_TOKENS_PER_CHUNK_PARAM;
import static machinum.processor.core.SplitFactory.OVERLAP_SIZE_PARAM;
import static machinum.util.TextUtil.*;

@Deprecated
@Slf4j
@Component
@RequiredArgsConstructor
public class CoT implements JsonSupport {

    private static final Function<MemberScope, String> DESCRIPTION_RESOLVER = JsonSupport.createComplexDescriptionResolver(ChainOfThoughts.class);
    private static final Function<TypeScope, String> TYPE_DESCRIPTION_RESOLVER = JsonSupport.createTypeDescriptionResolver(ChainOfThoughts.class);
    private static final String USER_TEMPLATE = """
            Remind me about web novel chapter we are talking about
            """;
    private static final String USER_QUESTIONS_TEMPLATE = """
            Remind me, what questions we already created?
            """;
    private static final String USER_QUESTION_TEMPLATE = """
            Give answers for given questions:   
            %s
            """;
    @Value("${app.split.overlap}")
    protected final Integer contentWindow;
    @Value("${app.split.overlap-size}")
    protected final Integer overlapSize;
    @Getter
    @Value("${spring.ai.ollama.chat.model}")
    protected final String chatModel;
    protected final RetryHelper retryHelper;
    @Value("classpath:prompts/extract/System.ST")
    private final Resource systemTemplate;
    @Value("classpath:prompts/custom/system/QuestionsSystem.ST")
    private final Resource questionsSystemTemplate;
    @Value("classpath:prompts/custom/system/AnswersSystem.ST")
    private final Resource answersSystemTemplate;
    @Value("classpath:prompts/custom/system/CotToolSystem.ST")
    private final Resource toolSystemTemplate;
    @Value("classpath:prompts/custom/Questions.json.ST")
    private final Resource questionsTemplate;
    @Value("classpath:prompts/custom/Answer.json.ST")
    private final Resource answersTemplate;
    @Value("classpath:prompts/custom/CotTool.ST")
    private final Resource cottoolTemplate;
    private final RawInfoTool rawInfoTool;
    private final SplitFactory splitFactory;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;
    private final Assistant assistant;

    private static String formatList(List<String> previousQuestions) {
        return String.join("\n", previousQuestions);
    }

    public FlowContext<Chapter> createCoT(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        var context = flowContext.context();

        var questions = firstStep(text, context, flowContext);
        var cot = secondStep(context, questions, flowContext);
        var cotWithTools = thirdStep(context, cot, flowContext);

        throw new IllegalArgumentException("Not ready");
//        return cotWithTools;
    }

    private Map<String, List<String>> firstStep(String text, String context, FlowContext<?> flowContext) {
        log.info("Prepare to create a questions from text: text={}...", toShortDescription(text));
        var splitStrategy = splitFactory.getSplitStrategy(SplitFactory.Type.BALANCED_PART, Map.of(
                MAX_TOKENS_PER_CHUNK_PARAM, contentWindow,
                OVERLAP_SIZE_PARAM, overlapSize
        ));
        var chunks = splitStrategy.split(text);
        var output = new HashMap<String, List<String>>();
        var previousQuestions = new ArrayList<String>();
        var counter = new AtomicInteger(0);

        for (String chunk : chunks) {
            var history = new ArrayList<Message>();

            history.add(new SystemMessage(questionsSystemTemplate));
            history.add(new UserMessage(USER_TEMPLATE));
            history.add(new AssistantMessage(context));

            if (!previousQuestions.isEmpty()) {
                history.add(new UserMessage(USER_QUESTIONS_TEMPLATE));
                history.add(new AssistantMessage(formatList(previousQuestions)));
            }

            log.debug("Working with chunk: number={}, text={}, tokens={}", counter.getAndIncrement(), toShortDescription(chunk), countTokensFast(chunk));

            var contextQuestions = createQuestions(chunk, history, flowContext);
            List<String> result = contextQuestions.entity();
            previousQuestions.addAll(result);
            output.put(chunk, result);
        }

        log.info("Questions created: amount={}", previousQuestions.size());

        return output;
    }

    private ChainOfThoughts secondStep(String context, Map<String, List<String>> questionsMap, FlowContext<?> flowContext) {
        log.info("Prepare to create a CoT for: questions={}", questionsMap.size());
        var cot = ChainOfThoughts.createNew();
        var counter = new AtomicInteger(0);

        for (var entry : questionsMap.entrySet()) {
            var chunk = entry.getKey();
            var questionsForChunk = entry.getValue();
            var questionsPartition = split(questionsForChunk, 5);

            for (var questions : questionsPartition) {
                var subCounter = new AtomicInteger();
                var history = new ArrayList<Message>();

                history.add(new SystemMessage(answersSystemTemplate));
                history.add(new UserMessage(USER_TEMPLATE));
                history.add(new AssistantMessage(context));

                if (!cot.listIsEmpty()) {
                    var truncatedStrings = truncateFromStart(cot.toStringList(), contentWindow);
                    var localCot = ChainOfThoughts.fromList(truncatedStrings);

                    history.add(new UserMessage(USER_QUESTION_TEMPLATE.formatted(formatList(localCot.questions()))));
                    history.add(new AssistantMessage(formatList(localCot.answers())));
                }

                log.debug("Working with chunk: number={}, text={}, tokens={}", counter.getAndIncrement(), toShortDescription(chunk), countTokensFast(chunk));

                var contextAnswers = giveAnswers(chunk, history, questions, counter, subCounter, flowContext);

                cot = cot.merge(contextAnswers.entity());
            }
        }

        log.info("CoT created: amount={}", cot.getQuestionsAndAnswers().size());

        return cot;
    }

    private ChainOfThoughts thirdStep(String context, ChainOfThoughts cot, FlowContext<?> flowContext) {
        log.info("Prepare to create list of tools, based on cot: cot={}", cot.size());
        var counter = new AtomicInteger(0);

        for (var qna : cot.getQuestionsAndAnswers()) {
            var history = new ArrayList<Message>();

            history.add(new SystemMessage(toolSystemTemplate));
            history.add(new UserMessage(USER_TEMPLATE));
            history.add(new AssistantMessage(context));

            var toolContext = createTool(qna, history, counter, flowContext);
            qna.setToolName(toolContext.result());
        }

        log.info("Tools created: amount={}", cot.getQuestionsAndAnswers().size());

        return cot;
    }

    private AssistantContext.Result createQuestions(String chunk, List<Message> history, FlowContext<?> flowContext) {
        return retryHelper.withRetry(() -> assistant.process(AssistantContext.builder()
                .flowContext(flowContext)
                .text(chunk)
                .actionResource(questionsTemplate)
                .history(history)
                .chatType(Assistant.Type.TRANSFORM)
                .outputClass(outputClass())
//                .inputs(Map.of(
//                        "schema", generateSchema(CustomTypeReference.of(outputClass()))
//                ))
                .mapper(this::mapQuestions)
                .tools(List.of(rawInfoTool))
                .build()));
    }

    private AssistantContext.Result giveAnswers(String chunk, List<Message> history, List<String> questions, AtomicInteger counter, AtomicInteger subCounter, FlowContext<?> flowContext) {
        return retryHelper.withRetry(() -> {
            var answerContext = AssistantContext.Result.createNew();

            log.debug("Working with questions chunk: number={}-{}, text={}, tokens={}",
                    counter.get(), subCounter.getAndIncrement(), TextUtil.toShortDescription(questions), countTokens(questions));

            var chunkContext = retryHelper.withRetry(() ->
                    assistant.process(AssistantContext.builder()
                            .flowContext(flowContext)
                            .text(chunk)
                            .actionResource(answersTemplate)
                            .history(history)
                            .chatType(Assistant.Type.TRANSFORM)
                            .outputClass(answerOutputClass())
                            .inputs(Map.of(
//                                    "schema", generateSchema(CustomTypeReference.of(answerOutputClass())),
                                    "questions", formatList(questions)
                            ))
                            .mapper(this::mapAnswers)
                            .tools(List.of(rawInfoTool))
                            .build()));

            ChainOfThoughts answerChunk = chunkContext.entity();
            if (answerChunk.size() < questions.size()) {
                var lostQuestions = detectLost(questions, answerChunk.questions());
                var subProcessorContext = giveAnswers(chunk, history, lostQuestions, counter, subCounter, flowContext);
                chunkContext.merge(subProcessorContext);
            }

            answerContext.merge(chunkContext);

            return answerContext;
        });
    }

    private AssistantContext.Result createTool(ChainOfThoughts.QuestionAndAnswer qna, List<Message> localHistory, AtomicInteger counter, FlowContext<?> flowContext) {
        return retryHelper.withRetry(() -> {
            log.debug("Working with qna chunk: number={}", counter.get());

            return assistant.process(AssistantContext.builder()
                    .flowContext(flowContext)
                    .text(qna.toString())
                    .actionResource(cottoolTemplate)
                    .history(localHistory)
                    .tools(List.of(rawInfoTool))
                    .build());
        });
    }

    @SneakyThrows
    private Object mapQuestions(String text) {
        log.error("Given format doesn't fit to expected format: {}...", toShortDescription(text));
        var objectMapper = objectMapperHolder.data();
        String localText = text;

        if (!localText.trim().startsWith("[") && localText.trim().endsWith("]")) {
            localText = "[%s".formatted(localText);
        }
        if (localText.trim().startsWith("[") && !localText.trim().endsWith("]")) {
            localText = "%s]".formatted(localText);
        }

        JsonNode jsonNode = objectMapper.readTree(localText);

        if (jsonNode.isArray()) {
            var first = jsonNode.get(0);
            if (first.isTextual()) {
                var reader = objectMapper.readerFor(CustomTypeReference.of(outputClass()));
                return reader.readValue(jsonNode);
            } else if (first.isObject() && first.has("items")) {
                var items = processItems(objectMapper, first.get("items"));
                if (Objects.nonNull(items)) {
                    return items;
                }
            }
        } else if (jsonNode.has("items")) {
            var items = processItems(objectMapper, jsonNode.get("items"));
            if (Objects.nonNull(items)) {
                return items;
            }
        } else if (jsonNode.has("questions")) {
            var items = processItems(objectMapper, jsonNode.get("questions"));
            if (Objects.nonNull(items)) {
                return items;
            }
        }

        throw new IllegalArgumentException("Broken format");
    }

    private Object processItems(ObjectMapper objectMapper, JsonNode items) throws IOException {
        if (items.isArray() && items.get(0).isTextual()) {
            var reader = objectMapper.readerFor(CustomTypeReference.of(outputClass()));
            return reader.readValue(items);
        }

        return null;
    }

    @SneakyThrows
    private Object mapAnswers(String text) {
        log.error("Given format doesn't fit to expected format: {}...", toShortDescription(text));

        var objectMapper = objectMapperHolder.data();
        var localText = text.replace("\"questions_and_answers\"", "\"questionsAndAnswers\"")
                .trim();

        var jsonNode = objectMapper.readTree(localText);
        if (jsonNode.isObject()) {
            var reader = objectMapper.readerFor(CustomTypeReference.of(answerOutputClass()));
            return reader.readValue(jsonNode);
        } else if (jsonNode.isArray()) {
            if (!jsonNode.isEmpty()) {
                JsonNode first = jsonNode.get(0);
                boolean hasAnswer = first.has("answer");
                boolean hasQuestion = first.has("question");

                if (hasQuestion && hasAnswer) {
                    var reader = objectMapper.readerFor(CustomTypeReference.of(cotListClass()));
                    return ChainOfThoughts.builder()
                            .questionsAndAnswers(reader.readValue(jsonNode))
                            .build();
                }
            }
        }

        throw new IllegalArgumentException("Broken format");
    }

    private ParameterizedTypeReference<List<String>> outputClass() {
        return new ParameterizedTypeReference<List<String>>() {
        };
    }

    private ParameterizedTypeReference<ChainOfThoughts> answerOutputClass() {
        return new ParameterizedTypeReference<ChainOfThoughts>() {
        };
    }

    private ParameterizedTypeReference<List<ChainOfThoughts.QuestionAndAnswer>> cotListClass() {
        return new ParameterizedTypeReference<List<ChainOfThoughts.QuestionAndAnswer>>() {
        };
    }

}
