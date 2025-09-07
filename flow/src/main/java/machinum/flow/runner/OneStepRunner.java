package machinum.flow.runner;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.FlowRunner;
import machinum.flow.core.StateManager;
import machinum.flow.exception.AppFlowException;
import machinum.flow.model.Flow;
import machinum.flow.model.FlowContext;
import machinum.flow.model.Pack;
import machinum.flow.model.helper.FlowContextActions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static machinum.flow.constant.FlowConstants.*;
import static machinum.flow.model.helper.FlowContextActions.iteration;
import static machinum.flow.model.helper.FlowContextActions.result;

/**
 * Implementation of FlowRunner that executes flow operations one step at a time.
 * This runner processes items sequentially through configured pipes, supporting
 * windowing operations, state management, and various aggregation functions.
 * It handles complex flow execution including bootstrap, refresh, and sink actions.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Sequential item processing with state transitions</li>
 *   <li>Windowed operations (tumbling, sliding, session windows)</li>
 *   <li>Aggregation functions (sum, count, average, custom)</li>
 *   <li>Error handling and exception strategies</li>
 *   <li>Performance measurement and monitoring</li>
 * </ul>
 *
 * @param <T> the type of items being processed in the flow
 */
@Slf4j
@RequiredArgsConstructor
public class OneStepRunner<T> implements FlowRunner<T> {

    /**
     * The flow configuration containing pipes, state definitions, and metadata.
     */
    @Getter
    private final Flow<T> flow;

    /**
     * Executes the flow starting from the specified state, processing items one by one.
     * This method initializes the runner context, executes bootstrap actions, processes
     * all items through configured pipes, handles windowed operations, and manages
     * state transitions.
     *
     * @param currentState the initial state to start flow execution from
     */
    @Override
    public void run(@NonNull Flow.State currentState) {
        log.debug("Executing flow for given state: {}", currentState);
        var sm = flow.getStateManager();
        var metadata = flow.getMetadata();
        var extendEnabled = (Boolean) metadata.getOrDefault(EXTEND_ENABLED, Boolean.FALSE);
        var flowContextRef = new AtomicReference<FlowContext<T>>(FlowContextActions.of(b -> b
                .state(currentState)
                .metadata(metadata)
                .flow(flow.copy(Function.identity()))
        ));

        var runnerContext = RunnerContext.<T>of(b -> b
                .flow(flow.copy(Function.identity()))
                .currentState(currentState)
                .sm(sm)
                .metadata(metadata)
                .extendEnabled(extendEnabled)
                .flowContextRef(flowContextRef)
                .windowBuffer(new WindowBuffer<>()));

        executeFlow(runnerContext);
    }

    private void executeFlow(RunnerContext<T> runnerContext) {
        runnerContext.executeBeforeAllAction();

        try {
            runnerContext.executeAroundAllAction(() -> processItems(runnerContext));
        } finally {
            runnerContext.executeAfterAllAction();
            log.debug("Flow has been executed for given state: {}", runnerContext.getCurrentState());
        }
    }

    private void processItems(RunnerContext<T> runnerContext) {
        var currentItemIndex = runnerContext.getLastProcessedItemIndex();
        var currentPipeIndex = runnerContext.getLastProcessorIndex();
        var originItem = runnerContext.getItem(currentItemIndex);

        prepareContext(runnerContext, originItem);

        for (int i = currentItemIndex; i < runnerContext.getSource().size(); i++) {
            processItem(runnerContext, i, currentPipeIndex);
        }

        // Process any remaining windows after all items are processed
        flushWindows(runnerContext);

        updateNextState(runnerContext);
    }

    private void flushWindows(RunnerContext<T> runnerContext) {
        var state = runnerContext.getCurrentState();
        runnerContext.getWindowBuffer().getAllWindows().forEach((windowId, contexts) -> {
            if (!contexts.isEmpty()) {
                var pipe = runnerContext.getWindowPipe(windowId);
                if (pipe != null) {
                    try {
                        processSinglePipe(runnerContext, -1, -1, ctx -> pipe.aggregate(contexts), state);
                    } catch (Exception e) {
                        handlePipeException(runnerContext, e);
                    }
                }
            }
        });
        runnerContext.getWindowBuffer().clear();
    }

    private void prepareContext(RunnerContext<T> runnerContext, T originItem) {
        if (runnerContext.isExtendEnabled()) {
            var extendContext = runnerContext.getFlowContext();
            runnerContext.updateFlowContext(runnerContext.executeExtendAction(extendContext));
        }

        var bootstrapContext = runnerContext.executeBootstrapAction(runnerContext.getFlowContext().withCurrentItem(originItem));
        runnerContext.updateFlowContext(bootstrapContext);
    }

    private void processItem(RunnerContext<T> runnerContext, int itemIndex, int startPipeIndex) {
        var itemFromSource = runnerContext.getItem(itemIndex);
        var refreshContext = runnerContext.getFlowContext().withCurrentItem(itemFromSource);
        var itemAfterRefresh = runnerContext.executeRefreshAction(refreshContext);
        var currentItemContext = refreshContext.withCurrentItem(itemAfterRefresh)
                .rearrange(FlowContext::iterationArg, iteration(itemIndex + 1));
        runnerContext.updateFlowContext(currentItemContext);

        var state = currentItemContext.getState();
        var pipes = Objects.requireNonNull(runnerContext.getStatePipe(state),
                "At least one pipe must be present for handling: " + state);

        var canUpdateState = runnerContext.executeAroundEachStateAction(() -> processPipes(runnerContext, itemIndex, startPipeIndex, pipes, state));

        if (canUpdateState) {
            runnerContext.saveCurrentState(itemIndex + 1, 0, state);
        }
    }

    private boolean processPipes(RunnerContext<T> runnerContext, int itemIndex, int startPipeIndex,
                                 List<Function<FlowContext<T>, FlowContext<T>>> pipes, Flow.State state) {
        var results = new ArrayList<Boolean>();
        try {
            for (int j = startPipeIndex; j < pipes.size(); j++) {
                try {
                    var pipe = pipes.get(j);
                    if (pipe instanceof WindowedPipe) {
                        var windowedResult = processWindowedPipe(runnerContext, itemIndex, j, (WindowedPipe<T>) pipe, state);
                        results.add(windowedResult);
                    } else {
                        processSinglePipe(runnerContext, itemIndex, j, pipe, state);
                        results.add(Boolean.TRUE);
                    }
                } catch (Exception e) {
                    handlePipeException(runnerContext, e);
                }
            }
        } finally {
            runnerContext.cleanEphemeralArgs();
        }

        return results.getLast();
    }

    private boolean processWindowedPipe(RunnerContext<T> runnerContext, int itemIndex, int pipeIndex,
                                        WindowedPipe<T> windowedPipe, Flow.State state) {
        var context = runnerContext.getFlowContext();
        var windowId = windowedPipe.getWindowId();

        runnerContext.getWindowBuffer().add(windowId, context);
        var window = runnerContext.getWindowBuffer().getWindow(windowId);
        int windowSize = window.size();

        // Check if window is ready for processing
        if (windowedPipe.shouldTrigger(window)) {
            var contexts = new ArrayList<>(window);

            // Process the aggregated result
            processSinglePipe(runnerContext, itemIndex, pipeIndex, ctx -> windowedPipe.aggregate(contexts), state);

            // Apply sliding window logic - remove elements based on slide
            int slideSize = windowedPipe.getWindow().getSlide();
            if (slideSize > 0 && slideSize <= windowSize) {
                runnerContext.getWindowBuffer().slideWindow(windowId, slideSize);
            } else if (windowedPipe.shouldClearAfterTrigger()) {
                runnerContext.getWindowBuffer().clearWindow(windowId);
            }

            return true;
        }

        // Update state even if we don't process anything yet
        if (windowedPipe.shouldUpdateState(window)) {
            runnerContext.saveCurrentState(itemIndex, pipeIndex + 1, state);
        }

        return false;
    }

    private FlowContext<T> processSinglePipe(RunnerContext<T> runnerContext, int itemIndex, int pipeIndex,
                                             Function<FlowContext<T>, FlowContext<T>> pipe, Flow.State state) {


        var context = runnerContext.getFlowContext().withCurrentPipeIndex(pipeIndex);
        var result = runnerContext.executeAroundEachCondition(context);

        FlowContext<T> eachContext;
        if (result.testResult()) {
            eachContext = runnerContext.executeAroundEachAction(
                    context, pipe);
        } else {
            eachContext = result.context();
        }

        var sinkEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_SINK));
        var stateUpdateEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_STATE_UPDATE));
        var output = eachContext.enableChanges();
        runnerContext.updateFlowContext(output);

        if (sinkEnabled) {
            runnerContext.executeSinkAction(eachContext.copy(b -> b));
        }

        if (stateUpdateEnabled) {
            runnerContext.saveCurrentState(itemIndex, pipeIndex + 1, state);
        }

        return output;
    }

    private void handlePipeException(RunnerContext<T> runnerContext, Exception e) {
        try {
            runnerContext.executeExceptionHandler(e);
        } catch (Exception ex) {
            //ignore
        }
        runnerContext.handleError(e);
    }

    private void updateNextState(RunnerContext<T> runnerContext) {
        var nextState = runnerContext.resolveNextState(runnerContext.getCurrentState());
        if (Objects.nonNull(nextState)) {
            runnerContext.saveCurrentState(0, 0, nextState);
        }
    }

    /**
     * Creates a new OneStepRunner instance with the specified sub-flow.
     * This method allows for creating specialized runners for sub-flows.
     * The measureWrapper parameter is ignored in this implementation.
     *
     * @param subFlow        the sub-flow to execute
     * @param measureWrapper wrapper function for performance measurement (ignored)
     * @return a new OneStepRunner instance configured for the sub-flow
     */
    @Override
    public FlowRunner<T> recreate(Flow<T> subFlow, Consumer<Runnable> measureWrapper) {
        return new OneStepRunner<>(subFlow);
    }

    /* ============= */

    /**
     * Window definition to be used with aggregate operations.
     * Defines different types of windows for data aggregation including
     * tumbling windows (non-overlapping), sliding windows (overlapping),
     * and session windows (activity-based).
     */
    public sealed interface Window permits Window.SessionWindow, Window.SlidingWindow, Window.TumblingWindow {

        /**
         * Creates a tumbling window of the specified size.
         * Tumbling windows are non-overlapping and trigger when the window is full.
         *
         * @param size the size of the tumbling window
         * @return a new TumblingWindow instance
         */
        static Window tumbling(int size) {
            return new TumblingWindow(size);
        }

        /**
         * Creates a sliding window with the specified size and slide interval.
         * Sliding windows overlap and move by the slide amount when triggered.
         *
         * @param size the size of the sliding window
         * @param slide the slide interval for the window
         * @return a new SlidingWindow instance
         */
        static Window sliding(int size, int slide) {
            return new SlidingWindow(size, slide);
        }

        /**
         * Creates a session window with the specified timeout.
         * Session windows group events that occur within a timeout period.
         *
         * @param timeout the timeout period for the session window
         * @return a new SessionWindow instance
         */
        static Window session(int timeout) {
            return new SessionWindow(timeout);
        }

        /**
         * Returns the size of the window.
         *
         * @return the window size
         */
        int getSize();

        /**
         * Returns the slide interval of the window.
         *
         * @return the slide interval
         */
        int getSlide();

        /**
         * Represents a tumbling window that processes data in non-overlapping chunks.
         * When the window reaches its size, it triggers processing and then clears.
         *
         * @param size the size of the tumbling window
         */
        record TumblingWindow(int size) implements Window {
            @Override
            public int getSize() {
                return size;
            }

            @Override
            public int getSlide() {
                return size; // For tumbling windows, slide equals size
            }
        }

        /**
         * Represents a sliding window that processes data in overlapping chunks.
         * The window moves by the slide amount after each trigger.
         *
         * @param size the size of the sliding window
         * @param slide the slide interval for the window
         */
        record SlidingWindow(int size, int slide) implements Window {
            @Override
            public int getSize() {
                return size;
            }

            @Override
            public int getSlide() {
                return slide;
            }
        }

        /**
         * Represents a session window that groups events within a timeout period.
         * Useful for processing bursts of activity with gaps.
         *
         * @param timeout the timeout period for the session window
         */
        record SessionWindow(int timeout) implements Window {
            @Override
            public int getSize() {
                return timeout;
            }

            @Override
            public int getSlide() {
                return 1; // Sessions don't have traditional slides
            }
        }

    }

    /**
     * Windowed pipe for processing data in windows.
     * Extends Function to allow integration with regular pipes while providing
     * windowing capabilities for aggregating data over time-based or count-based windows.
     *
     * @param <T> the type of items being processed
     */
    public interface WindowedPipe<T> extends Function<FlowContext<T>, FlowContext<T>> {

        /**
         * Returns the unique identifier for this windowed pipe.
         *
         * @return the window ID
         */
        String getWindowId();

        /**
         * Returns the window configuration for this pipe.
         *
         * @return the window definition
         */
        Window getWindow();

        /**
         * Determines whether the window should trigger processing based on the current contexts.
         *
         * @param contexts the list of contexts in the current window
         * @return true if the window should trigger processing, false otherwise
         */
        boolean shouldTrigger(List<FlowContext<T>> contexts);

        /**
         * Determines whether the window should be cleared after triggering.
         * Typically true for tumbling windows, false for sliding windows.
         *
         * @return true if the window should be cleared after trigger, false otherwise
         */
        boolean shouldClearAfterTrigger();

        /**
         * Aggregates the contexts in the window and returns the result.
         *
         * @param contexts the list of contexts to aggregate
         * @return the aggregated flow context
         */
        FlowContext<T> aggregate(List<FlowContext<T>> contexts);

        @Override
        default FlowContext<T> apply(FlowContext<T> context) {
            // This is a placeholder - actual processing happens in OneStepRunner
            return context;
        }

        /**
         * Determines whether the state should be updated even if processing hasn't triggered.
         * Useful for sliding windows that need to update state incrementally.
         *
         * @param contexts the list of contexts in the current window
         * @return true if state should be updated, false otherwise
         */
        default boolean shouldUpdateState(List<FlowContext<T>> contexts) {
            return false;
        }

    }

    /**
     * Aggregation function to be used with windows.
     * Provides common aggregation operations like sum, count, average, and custom mappings
     * for processing collections of FlowContext objects within windows.
     *
     * @param <T> the type of items being processed
     */
    @FunctionalInterface
    public interface Aggregation<T> {

        /**
         * Creates an aggregation that sums numeric values extracted from contexts.
         *
         * @param <T> the type of items in the contexts
         * @param extractor function to extract numeric values from contexts
         * @return an aggregation function that computes the sum
         */
        static <T> Aggregation<T> sum(Function<FlowContext<T>, Number> extractor) {
            return contexts -> {
                if (contexts.isEmpty()) return null;
                var sum = contexts.stream()
                        .map(extractor)
                        .mapToDouble(Number::doubleValue)
                        .sum();

                return contexts.getLast().rearrange(FlowContext::resultArg, result(sum));
            };
        }

        /**
         * Creates an aggregation that counts the number of contexts in the window.
         *
         * @param <T> the type of items in the contexts
         * @return an aggregation function that returns the count
         */
        static <T> Aggregation<T> count() {
            return contexts -> contexts.getLast().rearrange(FlowContext::resultArg, result((long) contexts.size()));
        }

        /**
         * Creates an aggregation that computes the average of numeric values extracted from contexts.
         *
         * @param <T> the type of items in the contexts
         * @param extractor function to extract numeric values from contexts
         * @return an aggregation function that computes the average
         */
        static <T> Aggregation<T> avg(Function<FlowContext<T>, Number> extractor) {
            return contexts -> {
                if (contexts.isEmpty()) return null;
                var avg = contexts.stream()
                        .map(extractor)
                        .mapToDouble(Number::doubleValue)
                        .average()
                        .orElse(0.0);

                return contexts.getLast().rearrange(FlowContext::resultArg, result(avg));
            };
        }

        /**
         * Creates an aggregation that maps contexts to a list of extracted values.
         *
         * @param <I> the type of items in the contexts
         * @param <U> the type of values to extract
         * @param extractor function to extract values from contexts
         * @return an aggregation function that returns a list of extracted values
         */
        static <I, U> Aggregation<I> map(Function<FlowContext<I>, U> extractor) {
            return contexts -> {
                var result = contexts.stream()
                        .map(extractor)
                        .toList();

                return contexts.getLast().rearrange(FlowContext::resultArg, result(result));
            };
        }

        /**
         * Creates an aggregation that packs contexts into Pack objects.
         *
         * @param <I> the type of items in the contexts
         * @param <U> the type of values to pack
         * @param extractor function to extract Pack objects from contexts
         * @return an aggregation function that returns a list of Pack objects
         */
        static <I, U> Aggregation<I> pack(Function<FlowContext<I>, Pack<I, U>> extractor) {
            return map(extractor);
        }

        /**
         * Creates an aggregation that collects all current items from contexts.
         *
         * @param <T> the type of items in the contexts
         * @return an aggregation function that returns a list of all items
         */
        static <T> Aggregation<T> items() {
            return map(FlowContext::getCurrentItem);
        }

        /**
         * Applies the aggregation to a list of contexts.
         *
         * @param contexts the list of contexts to aggregate
         * @return the aggregated flow context
         */
        FlowContext<T> apply(List<FlowContext<T>> contexts);

        /**
         * Chains this aggregation with another aggregation.
         *
         * @param after the aggregation to apply after this one
         * @return a new aggregation that applies this aggregation first, then the after aggregation
         */
        default Aggregation<T> andThen(Aggregation<T> after) {
            return contexts -> after.apply(List.of(apply(contexts)));
        }

        /**
         * Applies a function to the result of this aggregation.
         *
         * @param function the function to apply to the aggregation result
         * @return a new aggregation that applies the function to the result
         */
        default Aggregation<T> onResult(Function<FlowContext<T>, FlowContext<T>> function) {
            return contexts -> {
                var context = apply(contexts);
                return context.optionalValue(FlowContext::resultArg)
                        .map(o -> function.apply(context))
                        .orElse(context);
            };
        }

    }

    /**
     * Internal context holder for flow execution.
     * Encapsulates all the state and configuration needed during flow processing,
     * including flow configuration, current state, metadata, and window buffers.
     * Provides methods to execute various flow actions and manage state transitions.
     *
     * @param <T> the type of items being processed
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    private static class RunnerContext<T> {

        /**
         * The flow configuration being executed.
         */
        Flow<T> flow;

        /**
         * The current state of the flow execution.
         */
        Flow.State currentState;

        /**
         * The state manager for persisting execution state.
         */
        StateManager sm;

        /**
         * Metadata associated with the flow execution.
         */
        Map<String, Object> metadata;

        /**
         * Whether extend actions are enabled.
         */
        boolean extendEnabled;

        /**
         * Reference to the current flow context, updated during execution.
         */
        AtomicReference<FlowContext<T>> flowContextRef;

        /**
         * Buffer for managing windowed operations.
         */
        WindowBuffer<T> windowBuffer;

        public static <U> RunnerContext<U> of(Function<RunnerContextBuilder<U>, RunnerContextBuilder<U>> creator) {
            return creator.apply(builder()).build();
        }

        public void updateFlowContext(FlowContext<T> newContext) {
            flowContextRef.set(newContext);
        }

        public FlowContext<T> getFlowContext() {
            return flowContextRef.get();
        }

        public void cleanEphemeralArgs() {
            updateFlowContext(getFlowContext().withoutEphemeralArgs());
        }

        public List<Function<FlowContext<T>, FlowContext<T>>> getStatePipe(Flow.State state) {
            return getFlow().getStatePipes().get(state);
        }

        @Deprecated(forRemoval = true)
        public Flow<T> flowCopy(Function<Flow.FlowBuilder<T>, Flow.FlowBuilder<T>> fn) {
            return getFlow().copy(fn);
        }

        public FlowContext<T> executeExtendAction(FlowContext<T> context) {
            return getFlow().getExtendAction().apply(context);
        }

        public FlowContext<T> executeBootstrapAction(FlowContext<T> context) {
            return getFlow().getBootstrapAction().apply(context);
        }

        public T executeRefreshAction(FlowContext<T> context) {
            return getFlow().getRefreshAction().apply(context);
        }

        public void executeBeforeAllAction() {
            getFlow().getBeforeAllAction().accept(getFlowContext());
        }

        public void executeAroundAllAction(Runnable action) {
            getFlow().getAroundAllAction().accept(getFlowContext(), action);
        }

        public void executeAfterAllAction() {
            getFlow().getAfterAllAction().accept(getFlowContext());
        }

        public int getLastProcessedItemIndex() {
            return getSm().getLastProcessedItem(getMetadata());
        }

        public int getLastProcessorIndex() {
            return getSm().getLastProcessorIndex(getMetadata());
        }

        public List<T> getSource() {
            return getFlow().getSource();
        }

        public T getItem(Integer index) {
            return getSource().get(index);
        }

        public Flow.FlowPredicateResult<T> executeAroundEachCondition(FlowContext<T> context) {
            return getFlow().getAroundEachCondition().test(context);
        }

        public FlowContext<T> executeAroundEachAction(FlowContext<T> context, Function<FlowContext<T>, FlowContext<T>> action) {
            return Objects.requireNonNull(getFlow().getAroundEachAction().apply(context, action), "Response can't be null");
        }

        public boolean executeAroundEachStateAction(Supplier<Boolean> action) {
            var result = new AtomicBoolean();
            getFlow().getAroundEachStateAction().accept(getFlowContext(), () -> {
                result.set(action.get());
            });

            return result.get();
        }

        public void executeSinkAction(FlowContext<T> context) {
            getFlow().getSinkAction().accept(context);
        }

        public void saveCurrentState(int itemIndex, int pipeIndex, Flow.State state) {
            getSm().saveState(getMetadata(), itemIndex, pipeIndex, state);
        }

        public void executeExceptionHandler(Exception e) {
            getFlow().getExceptionAction().accept(getFlowContext(), e);
        }

        public void handleError(Exception e) {
            getFlow().getErrorStrategy().handleError(getFlowContext(), e);
        }

        public Flow.State resolveNextState(Flow.State initState) {
            return getFlow().nextState(initState);
        }

        public WindowedPipe<T> getWindowPipe(@NonNull String windowId) {
            var statePipes = getStatePipe(currentState);
            if (statePipes == null) return null;

            return statePipes.stream()
                    .filter(pipe -> pipe instanceof WindowedPipe)
                    .map(pipe -> (WindowedPipe<T>) pipe)
                    .filter(wp -> windowId.equals(wp.getWindowId()))
                    .findFirst()
                    .orElseThrow(() -> new AppFlowException("Window for given id not found: %s", windowId));
        }

    }

    /**
     * Buffer to store contexts for windowed operations.
     * Manages multiple windows concurrently, allowing contexts to be added to specific windows,
     * retrieved, and manipulated for sliding window operations.
     *
     * @param <T> the type of items being processed
     */
    private static class WindowBuffer<T> {

        /**
         * Thread-safe map storing windows by their IDs.
         */
        private final Map<String, List<FlowContext<T>>> windows = new ConcurrentHashMap<>();

        /**
         * Adds a context to the specified window.
         *
         * @param windowId the ID of the window to add to
         * @param context the context to add
         */
        public void add(String windowId, FlowContext<T> context) {
            windows.computeIfAbsent(windowId, k -> new ArrayList<>()).add(context);
        }

        /**
         * Retrieves all contexts for the specified window.
         *
         * @param windowId the ID of the window to retrieve
         * @return the list of contexts in the window, or empty list if window doesn't exist
         */
        public List<FlowContext<T>> getWindow(String windowId) {
            return windows.getOrDefault(windowId, List.of());
        }

        /**
         * Clears all contexts from the specified window.
         *
         * @param windowId the ID of the window to clear
         */
        public void clearWindow(String windowId) {
            windows.remove(windowId);
        }

        /**
         * Slides the window by removing the specified number of elements from the beginning.
         *
         * @param windowId the ID of the window to slide
         * @param slideSize the number of elements to remove from the beginning
         */
        public void slideWindow(String windowId, int slideSize) {
            var window = windows.get(windowId);
            if (window != null && slideSize > 0 && slideSize <= window.size()) {
                windows.put(windowId, new ArrayList<>(window.subList(slideSize, window.size())));
            }
        }

        /**
         * Returns a copy of all windows and their contexts.
         *
         * @return a map of window IDs to their context lists
         */
        public Map<String, List<FlowContext<T>>> getAllWindows() {
            return new HashMap<>(windows);
        }

        /**
         * Clears all windows and their contexts.
         */
        public void clear() {
            windows.clear();
        }

    }

    /**
     * Base implementation of a windowed pipe.
     * Provides a standard implementation of WindowedPipe with configurable window and aggregation.
     * Triggers when the window reaches its configured size and clears tumbling windows after processing.
     *
     * @param <T> the type of items being processed
     */
    @Value
    @RequiredArgsConstructor
    public static class BaseWindowedPipe<T> implements WindowedPipe<T> {

        /**
         * The unique identifier for this windowed pipe.
         */
        String windowId;

        /**
         * The window configuration defining size and behavior.
         */
        Window window;

        /**
         * The aggregation function to apply to windowed contexts.
         */
        Aggregation<T> aggregation;

        @Override
        public boolean shouldTrigger(List<FlowContext<T>> contexts) {
            return contexts.size() >= window.getSize();
        }

        @Override
        public boolean shouldClearAfterTrigger() {
            return window instanceof Window.TumblingWindow;
        }

        @Override
        public FlowContext<T> aggregate(List<FlowContext<T>> contexts) {
            return aggregation.apply(contexts);
        }

    }

    /**
     * Extension methods for Flow to support windowing operations.
     * Provides factory methods for creating windowed pipes with various configurations.
     */
    public static class FlowExtensions {

        /**
         * Creates a windowed pipe with the specified window ID, window configuration, and aggregation.
         *
         * @param <T> the type of items being processed
         * @param windowId the unique identifier for the window
         * @param window the window configuration
         * @param aggregation the aggregation function to apply
         * @return a new WindowedPipe instance
         */
        public static <T> WindowedPipe<T> window(String windowId, Window window, Aggregation<T> aggregation) {
            return new BaseWindowedPipe<>(windowId, window, aggregation);
        }

        /**
         * Creates a windowed pipe with an auto-generated window ID.
         *
         * @param <T> the type of items being processed
         * @param window the window configuration
         * @param aggregation the aggregation function to apply
         * @return a new WindowedPipe instance with auto-generated ID
         */
        public static <T> WindowedPipe<T> aggregate(Window window, Aggregation<T> aggregation) {
            String windowId = UUID.randomUUID().toString();
            return window(windowId, window, aggregation);
        }

    }

}
