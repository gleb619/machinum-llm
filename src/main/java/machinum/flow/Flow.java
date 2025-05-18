package machinum.flow;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static machinum.flow.Flow.ErrorStrategy.defaultStrategy;
import static machinum.flow.InMemoryStateManager.inMemory;
import static machinum.util.DurationUtil.DurationConfig.humanReadableDuration;
import static machinum.util.JavaUtil.newId;

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
    BiFunction<FlowContext<T>, Function<FlowContext<T>, FlowContext<T>>, FlowContext<T>> aroundEachAction = DefaultHandler.defaultAroundEach();

//    @Singular("aroundEachAction")
//    List<BiFunction<FlowContext<T>, Function<FlowContext<T>, FlowContext<T>>, FlowContext<T>>> aroundEachActions = new ArrayList<>();

    @Builder.Default
    Function<FlowContext<T>, T> refreshAction = FlowContext::getCurrentItem;

    @Builder.Default
    Function<FlowContext<T>, FlowContext<T>> bootstrapAction = Function.identity();

    @Builder.Default
    Function<FlowContext<T>, FlowContext<T>> extendAction = Function.identity();

    @Builder.Default
    Map<State, List<Function<FlowContext<T>, FlowContext<T>>>> statePipes = new LinkedHashMap<>();

    @Builder.Default
    Consumer<FlowContext<T>> sinkAction = (ctx) -> {
    };

    @Builder.Default
    StateManager stateManager = inMemory();

    @Builder.Default
    ErrorStrategy<T> errorStrategy = defaultStrategy();


    public static <T> Flow<T> from(Function<Flow.FlowBuilder<T>, Flow.FlowBuilder<T>> fn) {
        return fn.apply(Flow.builder()).build();
    }

    public static <T> Flow<T> from(T... args) {
        return from(Arrays.asList(args));
    }

    public static <T> Flow<T> from(Collection<T> source) {
        return from(b -> b.source(source));
    }

    public static <T> Flow<T> from(Collection<T> source, StateManager stateManager) {
        return from(b -> b.source(source)
                .stateManager(stateManager));
    }

    public Flow<T> withStateManager(StateManager stateManager) {
        return this.copy(b -> b.stateManager(stateManager));
    }

    public <I> Flow<I> map(Function<? super T, ? extends I> mapper) {
        return map((context, number, item) -> mapper.apply(item));
    }

    public <I> Flow<I> map(TriFunction<Map<String, Object>, Integer, ? super T, ? extends I> mapper) {
        var list = getSource();
        Flow<?> flow = copy(FlowBuilder::clearSource);

        return ((Flow<I>) flow).copy(b -> b.source(IntStream.range(0, list.size())
                .mapToObj(i -> mapper.apply(getMetadata(), i + 1, list.get(i)))
                .collect(Collectors.toList())));
    }

    public Flow<T> metadata(String key, Object value) {
        return copy(b -> b.metadata(key, value));
    }

    public Flow<T> metadata(Map<String, Object> map) {
        return copy(b -> b.metadata(map));
    }

    public Flow<T> beforeAll(Consumer<FlowContext<T>> action) {
        return copy(b -> b.beforeAllAction(action));
    }

    public Flow<T> afterAll(Consumer<FlowContext<T>> action) {
        return copy(b -> b.afterAllAction(action));
    }

    public Flow<T> aroundAll(BiConsumer<FlowContext<T>, Runnable> action) {
        return copy(b -> b.aroundAllAction(action));
    }

    public Flow<T> aroundEachState(BiConsumer<FlowContext<T>, Runnable> action) {
        return copy(b -> b.aroundEachStateAction(action));
    }

    public Flow<T> extend(Function<FlowContext<T>, FlowContext<T>> action) {
        return copy(b -> b.extendAction(action));
    }

    public Flow<T> bootstrap(Function<FlowContext<T>, FlowContext<T>> action) {
        return copy(b -> b.bootstrapAction(action));
    }

    public Flow<T> refresh(Function<FlowContext<T>, T> action) {
        return copy(b -> b.refreshAction(action));
    }

    public Flow<T> aroundEach(BiFunction<FlowContext<T>, Function<FlowContext<T>, FlowContext<T>>, FlowContext<T>> action) {
        return copy(b -> b.aroundEachAction(action));

//        if(DefaultHandler.isDefaultHandler(this.aroundEachAction)) {
//            return copy(b -> b.aroundEachActions(action));
//        } else {
//            var currentAction = this.aroundEachAction;
//            return copy(b -> b.aroundEachActions((ctx, fn) -> currentAction
//                    .andThen(after -> action.apply(after, fn))
//                    .apply(ctx, fn)
//            ));
//        }
    }

    public Flow<T> exception(BiConsumer<FlowContext<T>, Exception> action) {
        return copy(b -> b.exceptionAction(action));
    }

    public StateBuilder<T> onState(State state) {
        return new StateBuilder<>(this, state);
    }

    public Flow<T> sink(Consumer<FlowContext<T>> action) {
        return copy(b -> b.sinkAction(action));
    }

    public Flow<T> copy(Function<Flow.FlowBuilder<T>, Flow.FlowBuilder<T>> fn) {
        return fn.apply(this.toBuilder()).build();
    }

    public Flow.State nextState(Flow.State initState) {
        return Util.getNextKey(statePipes, initState);
    }

    public boolean isInitState(Flow.State initState) {
        return Util.isFirstKey(statePipes, initState);
    }

    public interface State {

        static State defaultState() {
            return new State() {
            };
        }

    }

    interface ErrorStrategy<T> {

        static <U> ErrorStrategy<U> defaultStrategy() {
            return new FailFast<>();
        }

        void handleError(FlowContext<T> context, Exception e);

    }

    @Value
    @Builder(toBuilder = true)
    public static class StateBuilder<T> {

        Flow<T> flow;

        State state;

        public StateBuilder(Flow<T> flow, State state) {
            this.flow = flow;
            this.state = state;
        }

        public StateBuilder<T> comment(Function<State, String> comment) {
            log.debug(comment.apply(state));
            return this;
        }

        public StateBuilder<T> pipe(Function<FlowContext<T>, FlowContext<T>> action) {
            flow.statePipes.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
            return this;
        }

        //TODO add metadata for pipe
        public StateBuilder<T> pipe(Map<String, Object> metadata, Function<FlowContext<T>, FlowContext<T>> action) {
            flow.statePipes.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
            return this;
        }

        public StateBuilder<T> nothing() {
            flow.statePipes.computeIfAbsent(state, k -> new ArrayList<>()).add(Function.identity());
            return this;
        }

        public StateBuilder<T> waitFor(Duration duration) {
            flow.statePipes.computeIfAbsent(state, k -> new ArrayList<>()).add(ctx -> {
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

        public StateBuilder<T> onState(State state) {
            return flow.onState(state);
        }

        public Flow<T> sink(Consumer<FlowContext<T>> action) {
            return flow.sink(action);
        }

        public Flow<T> build() {
            return flow;
        }

    }

    @Slf4j
    static class IgnoreErrors<T> implements ErrorStrategy<T> {

        @Override
        public void handleError(FlowContext<T> context, Exception e) {
            log.warn("Ignoring error: {}", e.getMessage());
        }

    }

    @Slf4j
    static class FailFast<T> implements ErrorStrategy<T> {

        @Override
        public void handleError(FlowContext<T> context, Exception e) {
            if (e instanceof FlowException fe) {
                if(fe.isShouldStopExecution()) {
                    throw new FlowException("FailFast strategy triggered", e);
                } else {
                    log.warn("Execution will no be stopped, due reason: {}", fe.getReason());
                    return;
                }
            }

            throw new FlowException("FailFast strategy triggered", e);
        }

    }

    private static class DefaultHandler<U> implements BiFunction<FlowContext<U>, Function<FlowContext<U>, FlowContext<U>>, FlowContext<U>> {

        public static <I> BiFunction<FlowContext<I>, Function<FlowContext<I>, FlowContext<I>>, FlowContext<I>> defaultAroundEach() {
            return new DefaultHandler<>();
        }

        public static <I> boolean isDefaultHandler(BiFunction<FlowContext<I>, Function<FlowContext<I>, FlowContext<I>>, FlowContext<I>> handler) {
            return handler instanceof Flow.DefaultHandler<I>;
        }

        @Override
        public FlowContext<U> apply(FlowContext<U> ctx, Function<FlowContext<U>, FlowContext<U>> fn) {
            return fn.apply(ctx);
        }

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    private static class Util {

        /**
         * Retrieves the key immediately following the specified key in a LinkedHashMap.
         *
         * @param map The input LinkedHashMap.
         * @param key The key to find the next key for.
         * @param <K> The type of keys in the map.
         * @return The next key after the specified key, or null if the key is not found or is the last key.
         */
        public static <K, V> K getNextKey(Map<K, V> map, K key) {
            boolean foundKey = false;

            for (K currentKey : map.keySet()) {
                if (foundKey) {
                    return currentKey; // Return the next key
                }
                if (currentKey.equals(key)) {
                    foundKey = true; // Mark that the specified key has been found
                }
            }

            return null; // Key not found or it was the last key
        }

        public static <K, V> boolean isFirstKey(Map<K, V> map, K key) {
            for (K currentKey : map.keySet()) {
                return currentKey.equals(key);
            }

            return false;
        }

    }

}

