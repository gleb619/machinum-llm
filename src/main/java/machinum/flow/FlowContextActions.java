package machinum.flow;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import machinum.model.Chunks;
import machinum.model.ObjectName;
import machinum.processor.core.ChapterWarning;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static machinum.flow.FlowContextConstants.*;

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

    public static FlowArgument<List<ObjectName>> glossary(List<ObjectName> value) {
        return createArg(GLOSSARY_PARAM, value);
    }

    public static FlowArgument<List<ObjectName>> consolidatedGlossary(List<ObjectName> value) {
        return createArg(CONSOLIDATED_GLOSSARY_PARAM, value);
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

    public static FlowArgument<ChapterWarning> warning(ChapterWarning chapterWarning) {
        return createArg(WARNING_PARAM, chapterWarning);
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

}
