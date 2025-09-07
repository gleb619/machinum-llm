package machinum.flow.model.helper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import machinum.flow.model.Chunks;
import machinum.flow.model.Flow;
import machinum.flow.model.FlowArgument;
import machinum.flow.model.FlowContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static machinum.flow.constant.FlowContextConstants.*;

/**
 * Utility class for creating FlowArgument instances and FlowContext objects.
 * This class provides static factory methods to construct various flow-related arguments
 * and contexts used in the flow processing pipeline. It serves as a central point for
 * building flow context components with consistent parameter naming and typing.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowContextActions {

    /**
     * Creates a FlowArgument for the context parameter.
     *
     * @param value the context string value
     * @return a FlowArgument containing the context value
     */
    public static FlowArgument<String> context(String value) {
        return createArg(CONTEXT_PARAM, value);
    }

    /**
     * Creates a FlowArgument for the consolidated context parameter.
     *
     * @param value the consolidated context string value
     * @return a FlowArgument containing the consolidated context value
     */
    public static FlowArgument<String> consolidatedContext(String value) {
        return createArg(CONSOLIDATED_CONTEXT_PARAM, value);
    }

    /**
     * Creates a FlowArgument for the text parameter.
     *
     * @param value the text string value
     * @return a FlowArgument containing the text value
     */
    public static FlowArgument<String> text(String value) {
        return createArg(TEXT_PARAM, value);
    }

    /**
     * Creates a FlowArgument for the translated text parameter.
     *
     * @param value the translated text string value
     * @return a FlowArgument containing the translated text value
     */
    public static FlowArgument<String> translatedText(String value) {
        return createArg(TRANSLATED_TEXT_PARAM, value);
    }

    /**
     * Creates a FlowArgument for the proofread parameter.
     *
     * @param value the proofread string value
     * @return a FlowArgument containing the proofread value
     */
    public static FlowArgument<String> proofread(String value) {
        return createArg(PROOFREAD_PARAM, value);
    }

    /**
     * Creates a FlowArgument for the chapter number parameter.
     *
     * @param number the chapter number
     * @return a FlowArgument containing the chapter number
     */
    public static FlowArgument<Integer> chapterNumber(Integer number) {
        return createArg(CHAPTER_NUMBER_PARAM, number);
    }

    /**
     * Creates a FlowArgument for the chunks parameter.
     *
     * @param chunks the chunks object
     * @return a FlowArgument containing the chunks
     */
    public static FlowArgument<Chunks> chunks(Chunks chunks) {
        return createArg(CHUNKS_PARAM, chunks);
    }

    /**
     * Creates a FlowArgument for the chunk parameter.
     *
     * @param chunk the chunk item
     * @return a FlowArgument containing the chunk
     */
    public static FlowArgument<Chunks.ChunkItem> chunk(Chunks.ChunkItem chunk) {
        return createArg(CHUNK_PARAM, chunk);
    }

    /**
     * Creates a FlowArgument for the translated chunks parameter.
     *
     * @param chunks the translated chunks object
     * @return a FlowArgument containing the translated chunks
     */
    public static FlowArgument<Chunks> translatedChunks(Chunks chunks) {
        return createArg(TRANSLATED_CHUNKS_PARAM, chunks);
    }

    /**
     * Creates a FlowArgument for the translated chunk parameter.
     *
     * @param chunk the translated chunk item
     * @return a FlowArgument containing the translated chunk
     */
    public static FlowArgument<Chunks.ChunkItem> translatedChunk(Chunks.ChunkItem chunk) {
        return createArg(TRANSLATED_CHUNK_PARAM, chunk);
    }

    /**
     * Creates a FlowArgument for the iteration parameter.
     *
     * @param value the iteration number
     * @return a FlowArgument containing the iteration value
     */
    public static FlowArgument<Integer> iteration(Integer value) {
        return createArg(ITERATION_PARAM, value);
    }

    /**
     * Creates a FlowArgument for the sub-iteration parameter.
     *
     * @param value the sub-iteration number
     * @return a FlowArgument containing the sub-iteration value
     */
    public static FlowArgument<Integer> subIteration(Integer value) {
        return createArg(SUB_ITERATION_PARAM, value);
    }

    /**
     * Creates an ephemeral FlowArgument for the result parameter.
     *
     * @param value the result object
     * @return an ephemeral FlowArgument containing the result value
     */
    public static FlowArgument<Object> result(Object value) {
        return createArg(RESULT_PARAM, value).asEphemeral();
    }

    /**
     * Creates a FlowArgument with the specified name and value.
     *
     * @param name  the name of the argument
     * @param value the value of the argument
     * @param <U>   the type of the value
     * @return a FlowArgument with the given name and value
     */
    public static <U> FlowArgument<U> createArg(@NonNull String name, U value) {
        return FlowArgument.<U>builder()
                .name(name)
                .value(value)
                .build();
    }

    /**
     * Creates a FlowContext with a single argument.
     *
     * @param arg the flow argument to include
     * @param <U> the type of the argument value
     * @param <T> the type of the flow context
     * @return a FlowContext containing the specified argument
     */
    public static <U, T> FlowContext<T> of(@NonNull FlowArgument<U> arg) {
        return of(b -> b.argument(arg));
    }

    /**
     * Creates a FlowContext with multiple arguments.
     *
     * @param args the flow arguments to include
     * @return a FlowContext containing the specified arguments
     */
    public static FlowContext<?> of(FlowArgument<?>... args) {
        return of(b -> b.arguments(List.of(args)));
    }

    /**
     * Creates a FlowContext with the specified flow state and empty metadata.
     *
     * @param state the flow state
     * @return a FlowContext with the given state
     */
    public static FlowContext<?> of(@NonNull Flow.State state) {
        return of(state, Map.of());
    }

    /**
     * Creates a FlowContext with the specified flow state and metadata.
     *
     * @param state    the flow state
     * @param metadata the metadata map
     * @param <T>      the type of the flow context
     * @return a FlowContext with the given state and metadata
     */
    public static <T> FlowContext<T> of(@NonNull Flow.State state, @NonNull Map<String, Object> metadata) {
        return of(b -> b.state(state)
                .metadata(metadata));
    }

    /**
     * Creates a FlowContext using a builder function.
     *
     * @param fn  the function to configure the FlowContextBuilder
     * @param <U> the type of the flow context
     * @return a FlowContext built using the provided function
     */
    public static <U> FlowContext<U> of(Function<FlowContext.FlowContextBuilder<U>, FlowContext.FlowContextBuilder<U>> fn) {
        return fn.apply(FlowContext.builder())
                .build();
    }

    /**
     * Marks a FlowArgument as an alternative.
     *
     * @param argument the flow argument to mark as alternative
     * @param <U>      the type of the argument value
     * @return the argument marked as alternative
     */
    public static <U> FlowArgument<U> alt(FlowArgument<U> argument) {
        return argument.asAlternative();
    }

    /**
     * Creates a function that extracts an alternative FlowArgument from a FlowContext.
     *
     * @param extractor the function to extract the argument
     * @param <T>       the input type of the flow context
     * @param <U>       the type of the argument value
     * @return a function that returns an alternative FlowArgument
     */
    public static <T, U> Function<FlowContext<? super T>, FlowArgument<? extends U>> alt(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return flowContext -> extractor.apply(flowContext).asAlternative();
    }

    /**
     * Retrieves or creates the iteration argument from the FlowContext.
     *
     * @param context the flow context
     * @param <T>     the type of the flow context
     * @return the iteration FlowArgument, defaulting to 1 if not present
     */
    public static <T> FlowArgument<Integer> iterationArg(FlowContext<T> context) {
        var argument = (FlowArgument) context.findArgument(ITERATION_PARAM, NEW_FLAG)
                .orElseGet(() -> createArg(ITERATION_PARAM, 1));
        return argument;
    }

    /**
     * Retrieves or creates the sub-iteration argument from the FlowContext.
     *
     * @param context the flow context
     * @param <T>     the type of the flow context
     * @return the sub-iteration FlowArgument, defaulting to 1 if not present
     */
    public static <T> FlowArgument<Integer> subIterationArg(FlowContext<T> context) {
        var argument = (FlowArgument) context.findArgument(SUB_ITERATION_PARAM, NEW_FLAG)
                .orElseGet(() -> createArg(SUB_ITERATION_PARAM, 1));
        return argument;
    }

    /**
     * Retrieves the result argument from the FlowContext.
     *
     * @param context the flow context
     * @param <T>     the type of the flow context
     * @return the result FlowArgument
     */
    public static <T> FlowArgument<Object> resultArg(FlowContext<T> context) {
        return context.arg(RESULT_PARAM);
    }

    /**
     * Retrieves an old (previous) argument from the FlowContext by name.
     *
     * @param context the flow context
     * @param name    the name of the argument
     * @param <T>     the type of the flow context
     * @param <U>     the type of the argument value
     * @return the old FlowArgument with the specified name
     */
    public static <T, U> FlowArgument<U> oldArg(FlowContext<T> context, String name) {
        return context.getArgument(name, OLD_FLAG);
    }

}
