package machinum.flow;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Default handler for around-each actions in the flow.
 */
public class FlowAction<U> implements BiFunction<FlowContext<U>, Function<FlowContext<U>, FlowContext<U>>, FlowContext<U>> {

    /**
     * Creates a default handler instance.
     *
     * @param <I> The type of items in the flow.
     * @return A new default handler.
     */
    public static <I> BiFunction<FlowContext<I>, Function<FlowContext<I>, FlowContext<I>>, FlowContext<I>> defaultAroundEach() {
        return new FlowAction<>();
    }

    /**
     * Checks if the given handler is the default handler.
     *
     * @param handler The handler to check.
     * @param <I>     The type of items in the flow.
     * @return true if it's the default handler, false otherwise.
     */
    public static <I> boolean isDefaultHandler(BiFunction<FlowContext<I>, Function<FlowContext<I>, FlowContext<I>>, FlowContext<I>> handler) {
        return handler instanceof FlowAction<I>;
    }

    @Override
    public FlowContext<U> apply(FlowContext<U> context, Function<FlowContext<U>, FlowContext<U>> function) {
        return function.apply(context);
    }

}
