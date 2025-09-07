package machinum.flow.model;

import lombok.Builder;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.ErrorStrategy;
import machinum.flow.core.StateBuilder;
import machinum.flow.core.StateManager;
import machinum.flow.function.TriFunction;
import machinum.flow.model.helper.FlowActions;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static machinum.flow.core.ErrorStrategy.defaultStrategy;
import static machinum.flow.core.InMemoryStateManager.inMemory;
import static machinum.flow.util.FlowUtil.newId;

/**
 * Represents a flow for processing items through a series of states and actions.
 * This class provides a fluent API for building and configuring flows.
 *
 * @param <T> The type of items processed by the flow.
 */
@Slf4j
@Value
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
public class Flow<T> {

    @ToString.Include
    @Builder.Default
    String id = newId("fl-");

    @Singular("source")
    List<T> source;

    @Singular("metadata")
    Map<String, Object> metadata;

    @Builder.Default
    Consumer<FlowContext<T>> beforeAllAction = ctx -> {
    };

    @Builder.Default
    Consumer<FlowContext<T>> afterAllAction = ctx -> {
    };

    @Builder.Default
    BiConsumer<FlowContext<T>, Exception> exceptionAction = (ctx, e) -> {
    };

    @Builder.Default
    BiConsumer<FlowContext<T>, Runnable> aroundAllAction = (ctx, run) -> run.run();

    @Builder.Default
    BiConsumer<FlowContext<T>, Runnable> aroundEachStateAction = (ctx, run) -> run.run();

    @Builder.Default
    BiFunction<FlowContext<T>, Function<FlowContext<T>, FlowContext<T>>, FlowContext<T>> aroundEachAction = FlowAction.defaultAroundEach();

    @Builder.Default
    FlowPredicate<T> aroundEachCondition = FlowPredicateResult::accept;

    @Builder.Default
    Function<FlowContext<T>, T> refreshAction = FlowContext::getCurrentItem;

    @Builder.Default
    Function<FlowContext<T>, FlowContext<T>> bootstrapAction = Function.identity();

    @Builder.Default
    Function<FlowContext<T>, FlowContext<T>> extendAction = Function.identity();

    @Builder.Default
    Map<State, List<Function<FlowContext<T>, FlowContext<T>>>> statePipes = new LinkedHashMap<>();

    @Builder.Default
    Consumer<FlowContext<T>> sinkAction = ctx -> {
    };

    @Builder.Default
    StateManager stateManager = inMemory();

    @Builder.Default
    ErrorStrategy<T> errorStrategy = defaultStrategy();

    /**
     * Creates a flow from a builder function.
     *
     * @param fn  The builder function.
     * @param <T> The type of items in the flow.
     * @return The created flow.
     */
    public static <T> Flow<T> from(Function<FlowBuilder<T>, FlowBuilder<T>> fn) {
        return fn.apply(Flow.builder()).build();
    }

    /**
     * Creates a flow from variable arguments.
     *
     * @param args The source items.
     * @param <T>  The type of items.
     * @return The created flow.
     */
    public static <T> Flow<T> from(T... args) {
        return from(Arrays.asList(args));
    }

    /**
     * Creates a flow from a collection.
     *
     * @param source The source collection.
     * @param <T>    The type of items.
     * @return The created flow.
     */
    public static <T> Flow<T> from(Collection<T> source) {
        return from(b -> b.source(source));
    }

    /**
     * Creates a flow from a collection with a specific state manager.
     *
     * @param source       The source collection.
     * @param stateManager The state manager.
     * @param <T>          The type of items.
     * @return The created flow.
     */
    public static <T> Flow<T> from(Collection<T> source, StateManager stateManager) {
        return from(b -> b.source(source).stateManager(stateManager));
    }

    /**
     * Creates a new flow with the specified state manager.
     *
     * @param stateManager The state manager.
     * @return The new flow.
     */
    public Flow<T> withStateManager(StateManager stateManager) {
        return this.copy(b -> b.stateManager(stateManager));
    }

    /**
     * Maps the flow items using a simple mapper function.
     *
     * @param mapper The mapper function.
     * @param <I>    The type of the mapped items.
     * @return The mapped flow.
     */
    public <I> Flow<I> map(Function<? super T, ? extends I> mapper) {
        return map((context, number, item) -> mapper.apply(item));
    }

    /**
     * Maps the flow items using a tri-function mapper.
     *
     * @param mapper The mapper function.
     * @param <I>    The type of the mapped items.
     * @return The mapped flow.
     */
    public <I> Flow<I> map(TriFunction<Map<String, Object>, Integer, ? super T, ? extends I> mapper) {
        var list = getSource();
        Flow<?> flow = copy(FlowBuilder::clearSource);

        return ((Flow<I>) flow).copy(b -> b.source(IntStream.range(0, list.size())
                .mapToObj(i -> mapper.apply(getMetadata(), i + 1, list.get(i)))
                .collect(Collectors.toList())));
    }

    /**
     * Adds metadata to the flow.
     *
     * @param key   The metadata key.
     * @param value The metadata value.
     * @return The updated flow.
     */
    public Flow<T> metadata(String key, Object value) {
        return copy(b -> b.metadata(key, value));
    }

    /**
     * Adds metadata map to the flow.
     *
     * @param map The metadata map.
     * @return The updated flow.
     */
    public Flow<T> metadata(Map<String, Object> map) {
        return copy(b -> b.metadata(map));
    }

    /**
     * Sets the before-all action.
     *
     * @param action The action to perform before all processing.
     * @return The updated flow.
     */
    public Flow<T> beforeAll(Consumer<FlowContext<T>> action) {
        return copy(b -> b.beforeAllAction(action));
    }

    /**
     * Sets the after-all action.
     *
     * @param action The action to perform after all processing.
     * @return The updated flow.
     */
    public Flow<T> afterAll(Consumer<FlowContext<T>> action) {
        return copy(b -> b.afterAllAction(action));
    }

    /**
     * Sets the around-all action.
     *
     * @param action The around-all action.
     * @return The updated flow.
     */
    public Flow<T> aroundAll(BiConsumer<FlowContext<T>, Runnable> action) {
        return copy(b -> b.aroundAllAction(action));
    }

    /**
     * Sets the around-each-state action.
     *
     * @param action The around-each-state action.
     * @return The updated flow.
     */
    public Flow<T> aroundEachState(BiConsumer<FlowContext<T>, Runnable> action) {
        return copy(b -> b.aroundEachStateAction(action));
    }

    /**
     * Sets the extend action.
     *
     * @param action The extend action.
     * @return The updated flow.
     */
    public Flow<T> extend(Function<FlowContext<T>, FlowContext<T>> action) {
        return copy(b -> b.extendAction(action));
    }

    /**
     * Sets the bootstrap action.
     *
     * @param action The bootstrap action.
     * @return The updated flow.
     */
    public Flow<T> bootstrap(Function<FlowContext<T>, FlowContext<T>> action) {
        return copy(b -> b.bootstrapAction(action));
    }

    /**
     * Sets the refresh action.
     *
     * @param action The refresh action.
     * @return The updated flow.
     */
    public Flow<T> refresh(Function<FlowContext<T>, T> action) {
        return copy(b -> b.refreshAction(action));
    }

    /**
     * Sets the each-condition predicate.
     *
     * @param condition The condition predicate.
     * @return The updated flow.
     */
    public Flow<T> eachCondition(FlowPredicate<T> condition) {
        return copy(b -> b.aroundEachCondition(condition));
    }

    /**
     * Sets the around-each action.
     *
     * @param action The around-each action.
     * @return The updated flow.
     */
    public Flow<T> aroundEach(BiFunction<FlowContext<T>, Function<FlowContext<T>, FlowContext<T>>, FlowContext<T>> action) {
        return copy(b -> b.aroundEachAction(action));
    }

    /**
     * Sets the exception action.
     *
     * @param action The exception action.
     * @return The updated flow.
     */
    public Flow<T> exception(BiConsumer<FlowContext<T>, Exception> action) {
        return copy(b -> b.exceptionAction(action));
    }

    /**
     * Creates a state builder for the specified state.
     *
     * @param state The state to configure.
     * @return The state builder.
     */
    public StateBuilder<T> onState(State state) {
        return new StateBuilder<>(this, state);
    }

    /**
     * Sets the sink action.
     *
     * @param action The sink action.
     * @return The updated flow.
     */
    public Flow<T> sink(Consumer<FlowContext<T>> action) {
        return copy(b -> b.sinkAction(action));
    }

    /**
     * Creates a copy of the flow with modifications.
     *
     * @param fn The function to modify the builder.
     * @return The modified flow.
     */
    public Flow<T> copy(Function<FlowBuilder<T>, FlowBuilder<T>> fn) {
        var stateMap = new LinkedHashMap<>(getStatePipes());
        var metadata = new HashMap<>(getMetadata());
        var source = new ArrayList<>(getSource());
        return fn.apply(this.toBuilder()
                .clearMetadata()
                .metadata(metadata)
                .clearSource()
                .source(source)
                .statePipes(stateMap)).build();
    }

    /**
     * Gets the next state after the specified state.
     *
     * @param initState The current state.
     * @return The next state, or null if none.
     */
    public State nextState(State initState) {
        return FlowActions.getNextKey(statePipes, initState);
    }

    /**
     * Checks if the specified state is the initial state.
     *
     * @param initState The state to check.
     * @return true if it's the initial state, false otherwise.
     */
    public boolean isInitState(State initState) {
        return FlowActions.isFirstKey(statePipes, initState);
    }

    /**
     * Represents a state in the flow.
     */
    public interface State {

        /**
         * Creates a default state.
         *
         * @return The default state.
         */
        static State defaultState() {
            return new State() {
            };
        }
    }

    /**
     * Predicate for flow conditions.
     *
     * @param <T> The type of items.
     */
    @FunctionalInterface
    public interface FlowPredicate<T> {

        /**
         * Evaluates this predicate on the given context.
         *
         * @param context The flow context.
         * @return The predicate result.
         */
        FlowPredicateResult<T> test(FlowContext<T> context);
    }

    /**
     * Result of a flow predicate evaluation.
     *
     * @param <T>        The type of items.
     * @param context    The flow context.
     * @param testResult The test result.
     */
    public record FlowPredicateResult<T>(FlowContext<T> context, boolean testResult) {

        /**
         * Creates an accept result.
         *
         * @param context The flow context.
         * @param <U>     The type of items.
         * @return The accept result.
         */
        public static <U> FlowPredicateResult<U> accept(FlowContext<U> context) {
            return new FlowPredicateResult<>(context, true);
        }

        /**
         * Creates a reject result.
         *
         * @param context The flow context.
         * @param <U>     The type of items.
         * @return The reject result.
         */
        public static <U> FlowPredicateResult<U> reject(FlowContext<U> context) {
            return new FlowPredicateResult<>(context, false);
        }
    }
}
