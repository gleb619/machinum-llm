package machinum.processor.core;

import lombok.*;
import machinum.flow.model.FlowContext;
import machinum.flow.model.Mergeable;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.ObjectName;
import machinum.processor.client.AiClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;

import java.util.*;
import java.util.function.Function;

import static machinum.util.JavaUtil.newId;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AssistantContext implements Mergeable<AssistantContext> {

    @Builder.Default
    @ToString.Include
    @EqualsAndHashCode.Include
    private String id = newId("actx-");

    @Builder.Default
    @ToString.Include
    @EqualsAndHashCode.Include
    private String operation = "<unnamed>";

    @Builder.Default
    private String text = "";

    private Resource actionResource;

    @Singular
    private Map<String, String> inputs = new HashMap<>();

    @Builder.Default
    private List<Message> history = new ArrayList<>();

    @Builder.Default
    private OutputType outputType = OutputType.STRING;

    private ParameterizedTypeReference<?> outputClass;

    @Builder.Default
    private List<String> chunks = new ArrayList<>();

    @Builder.Default
    private Assistant.Type chatType = Assistant.Type.CHAT;

    @Builder.Default
    private Function<String, Object> mapper = (s) -> {
        throw new IllegalArgumentException("Unsupported");
    };

    @Builder.Default
    private Function<OllamaOptions, OllamaOptions> customizeChatOptions = Function.identity();

    @Deprecated
    @Builder.Default
    private List<ObjectName> names = new ArrayList<>();

    @Deprecated
    @Builder.Default
    private List<Object> tools = new ArrayList<>();

    @Singular("metadata")
    private Map<String, String> metadata = new HashMap<>();

    @Builder.Default
    private AiClient.Provider provider = AiClient.Provider.OLLAMA;

    @Builder.Default
    private FlowContext<?> flowContext = FlowContextActions.of();

    @ToString.Include
    @Builder.Default
    @EqualsAndHashCode.Include
    private Result result = Result.createNew();


    @Deprecated
    public static AssistantContext forRaw(String text, Resource actionResource, List<Message> history) {
        return forTransform(text, actionResource, history, Map.of(), new ArrayList<>());
    }

    @Deprecated
    public static AssistantContext forTransform(String text, Resource actionResource, List<Message> history,
                                                Map<String, String> inputs, List<String> chunks) {
        return AssistantContext.builder()
                .text(text)
                .actionResource(actionResource)
                .history(history)
                .inputs(inputs)
                .chunks(chunks)
                .build();
    }

    public AssistantContext copy(Function<AssistantContextBuilder, AssistantContextBuilder> builderFn) {
        return builderFn.apply(toBuilder())
                .build();
    }

    public String input(String key) {
        return getInputs().getOrDefault(key, "");
    }

    @Deprecated
    public String metadata(String key) {
        return getMetadata().getOrDefault(key, "No data");
    }

    public AssistantContext addResultHistory(String history) {
        result.getResultHistory().add(history);

        return this;
    }

    public String getMostResultFromHistory() {
        return result.getResultHistory().stream().max(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new NullPointerException("History is empty"));
    }

    @Override
    public AssistantContext recreate() {
        return copy(Function.identity());
    }

    @Override
    public AssistantContext merge(AssistantContext other) {
        return toBuilder()
                .result(result.merge(other.getResult()))
                .build();
    }

    public enum OutputType {

        STRING,
        JSON,
        PROPERTIES

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    public static class Result implements Mergeable<Result> {

        @Builder.Default
        @ToString.Include
        @EqualsAndHashCode.Include
        private String id = newId("rslt-");

        //TODO reod to List<String>
        @Builder.Default
        private StringBuilder result = new StringBuilder();

        private Object entity;

        @Builder.Default
        private List<Message> history = new ArrayList<>();

        private OllamaOptions ollamaOptions;

        private AssistantContext context;

        @Builder.Default
        private List<String> resultHistory = new ArrayList<>();

        public static Result createNew() {
            return createNew(Function.identity());
        }

        public static Result createNew(Function<Result.ResultBuilder, Result.ResultBuilder> builderFn) {
            return builderFn.apply(Result.builder()).build();
        }

        public static Result of(String text) {
            return Result.builder()
                    .result(new StringBuilder(text))
                    .build();
        }

        public Result appendResult(String content) {
            result.append(content);
            return this;
        }

        public Result replaceResult(String content) {
            result.setLength(0);
            result.append(content);
            return this;
        }

        public String result() {
            return result.toString();
        }

        public String result(Function<String, String> customize) {
            return customize.apply(result());
        }

        public <T> T entity() {
            return (T) entity;
        }

        public Result copy(Function<Result.ResultBuilder, Result.ResultBuilder> fn) {
            return fn.apply(this.toBuilder())
                    .build();
        }

        @Override
        public Result recreate() {
            return toBuilder()
                    .build();
        }

        @Override
        public Result merge(Result other) {
            var newThis = recreate();

            var otherResult = other.getResult();
            if (Objects.nonNull(otherResult) && !otherResult.isEmpty()) {
                newThis.result.append('\n').append(otherResult);
            }

            if (Objects.isNull(entity)) {
                if (other.getEntity() instanceof List otherList) {
                    entity = new ArrayList<>(otherList);
                } else if (other.getEntity() instanceof Mergeable otherMergeable) {
                    entity = otherMergeable.recreate();
                } else {
                    entity = other.getEntity();
                }
            } else if (entity instanceof List list && other.getEntity() instanceof List otherList) {
                list.addAll(otherList);
            } else if (entity instanceof List list && !(other.getEntity() instanceof List)) {
                list.add(other.getEntity());
            } else if (entity instanceof Mergeable mergeable && other.getEntity() instanceof Mergeable otherMergeable) {
                entity = mergeable.merge(otherMergeable);
            }

            newThis.history.clear();
            newThis.history.addAll(other.getHistory());

            return newThis;
        }

    }

}
