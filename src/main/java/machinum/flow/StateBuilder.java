package machinum.flow;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.Flow.State;
import machinum.flow.OneStepRunner.Aggregation;
import machinum.flow.OneStepRunner.Window;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static machinum.flow.OneStepRunner.FlowExtensions.aggregate;
import static machinum.util.DurationMeasureUtil.DurationConfig.humanReadableDuration;

/**
 * Builder for configuring states in a flow.
 *
 * @param <T> The type of items in the flow.
 */
@Slf4j
@Value
@NonFinal
public class StateBuilder<T> {

    Flow<T> flow;
    State state;

    /**
     * Adds a comment to the state for debugging purposes.
     *
     * @param comment A function that generates a comment string from the state.
     * @return This StateBuilder for chaining.
     */
    public StateBuilder<T> comment(Function<State, String> comment) {
        log.debug(comment.apply(state));
        return this;
    }

    /**
     * Adds a stateless pipe to the state.
     *
     * @param action The action to perform.
     * @return This StateBuilder for chaining.
     */
    public StateBuilder<T> pipeStateless(Function<FlowContext<T>, FlowContext<T>> action) {
        getFlow().getStatePipes().computeIfAbsent(state, k -> new ArrayList<>())
                .add(context -> action.apply(context).preventChanges());
        return this;
    }

    /**
     * Adds a pipe to the state.
     *
     * @param action The action to perform.
     * @return This StateBuilder for chaining.
     */
    public StateBuilder<T> pipe(Function<FlowContext<T>, FlowContext<T>> action) {
        getFlow().getStatePipes().computeIfAbsent(state, k -> new ArrayList<>()).add(action);
        return this;
    }

    /**
     * Adds a windowed aggregation pipe to the state.
     *
     * @param window The window configuration.
     * @param action The aggregation action.
     * @return This StateBuilder for chaining.
     */
    public StateBuilder<T> window(Window window, Aggregation<T> action) {
        return pipe(aggregate(window, action));
    }

    /**
     * Adds a no-op pipe to the state.
     *
     * @return This StateBuilder for chaining.
     */
    public StateBuilder<T> nothing() {
        getFlow().getStatePipes().computeIfAbsent(state, k -> new ArrayList<>())
                .add(Function.identity());
        return this;
    }

    /**
     * Adds a wait pipe to the state.
     *
     * @param duration The duration to wait.
     * @return This StateBuilder for chaining.
     */
    public StateBuilder<T> waitFor(Duration duration) {
        getFlow().getStatePipes().computeIfAbsent(state, k -> new ArrayList<>())
                .add(ctx -> {
                    try {
                        log.debug("Waiting for {} to cool down GPU", humanReadableDuration(duration));
                        TimeUnit.MILLISECONDS.sleep(duration.toMillis());
                    } catch (InterruptedException e) {
                        return ExceptionUtils.rethrow(e);
                    }
                    return ctx;
                });
        return this;
    }

    /**
     * Transitions to configuring another state.
     *
     * @param state The new state to configure.
     * @return A new StateBuilder for the specified state.
     */
    public StateBuilder<T> onState(State state) {
        return flow.onState(state);
    }

    /**
     * Sets the sink action for the flow.
     *
     * @param action The sink action.
     * @return The updated flow.
     */
    public Flow<T> sink(Consumer<FlowContext<T>> action) {
        return flow.sink(action);
    }

    /**
     * Builds the flow.
     *
     * @return The configured flow.
     */
    public Flow<T> build() {
        return flow;
    }
}
