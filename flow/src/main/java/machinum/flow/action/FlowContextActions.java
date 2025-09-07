package machinum.flow.action;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import machinum.flow.argument.FlowArgument;
import machinum.flow.core.Flow;
import machinum.flow.core.FlowContext;
import machinum.flow.model.Chunks;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static machinum.flow.constant.FlowContextConstants.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowContextActions {

    public static FlowArgument<String> context(String value) {
        return createArg(CONTEXT_PARAM, value);
    }

    public static FlowArgument<String> consolidatedContext(String value) {
        return createArg(CONSOLIDATED_CONTEXT_PARAM, value);
    }

    public static FlowArgument<String> text(String value) {
        return createArg(TEXT_PARAM, value);
    }

    public static FlowArgument<String> translatedText(String value) {
        return createArg(TRANSLATED_TEXT_PARAM, value);
    }

    public static FlowArgument<String> proofread(String value) {
        return createArg(PROOFREAD_PARAM, value);
    }

    public static FlowArgument<Integer> chapterNumber(Integer number) {
        return createArg(CHAPTER_NUMBER_PARAM, number);
    }

    public static FlowArgument<Chunks> chunks(Chunks chunks) {
        return createArg(CHUNKS_PARAM, chunks);
    }

    public static FlowArgument<Chunks.ChunkItem> chunk(Chunks.ChunkItem chunk) {
        return createArg(CHUNK_PARAM, chunk);
    }

    public static FlowArgument<Chunks> translatedChunks(Chunks chunks) {
        return createArg(TRANSLATED_CHUNKS_PARAM, chunks);
    }

    public static FlowArgument<Chunks.ChunkItem> translatedChunk(Chunks.ChunkItem chunk) {
        return createArg(TRANSLATED_CHUNK_PARAM, chunk);
    }

    public static FlowArgument<Integer> iteration(Integer value) {
        return createArg(ITERATION_PARAM, value);
    }

    public static FlowArgument<Integer> subIteration(Integer value) {
        return createArg(SUB_ITERATION_PARAM, value);
    }

    public static FlowArgument<Object> result(Object value) {
        return createArg(RESULT_PARAM, value).asEphemeral();
    }

    public static <U> FlowArgument<U> createArg(@NonNull String name, U value) {
        return FlowArgument.<U>builder()
                .name(name)
                .value(value)
                .build();
    }

    public static <U, T> FlowContext<T> of(@NonNull FlowArgument<U> arg) {
        return of(b -> b.argument(arg));
    }

    public static FlowContext<?> of(FlowArgument<?>... args) {
        return of(b -> b.arguments(List.of(args)));
    }

    public static FlowContext<?> of(@NonNull Flow.State state) {
        return of(state, Map.of());
    }

    public static <T> FlowContext<T> of(@NonNull Flow.State state, @NonNull Map<String, Object> metadata) {
        return of(b -> b.state(state)
                .metadata(metadata));
    }

    public static <U> FlowContext<U> of(Function<FlowContext.FlowContextBuilder<U>, FlowContext.FlowContextBuilder<U>> fn) {
        return fn.apply(FlowContext.builder())
                .build();
    }

    public static <U> FlowArgument<U> alt(FlowArgument<U> argument) {
        return argument.asAlternative();
    }

    public static <T, U> Function<FlowContext<? super T>, FlowArgument<? extends U>> alt(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return flowContext -> extractor.apply(flowContext).asAlternative();
    }

    public static <T> FlowArgument<Integer> iterationArg(FlowContext<T> context) {
        var argument = (FlowArgument) context.findArgument(ITERATION_PARAM, NEW_FLAG)
                .orElseGet(() -> createArg(ITERATION_PARAM, 1));
        return argument;
    }

    public static <T> FlowArgument<Integer> subIterationArg(FlowContext<T> context) {
        var argument = (FlowArgument) context.findArgument(SUB_ITERATION_PARAM, NEW_FLAG)
                .orElseGet(() -> createArg(SUB_ITERATION_PARAM, 1));
        return argument;
    }

    public static <T> FlowArgument<Object> resultArg(FlowContext<T> context) {
        return context.arg(RESULT_PARAM);
    }

    public static <T, U> FlowArgument<U> oldArg(FlowContext<T> context, String name) {
        return context.getArgument(name, OLD_FLAG);
    }

}
