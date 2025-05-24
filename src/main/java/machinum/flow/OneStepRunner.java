package machinum.flow;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static machinum.config.Constants.*;
import static machinum.flow.FlowContextActions.iteration;
import static machinum.flow.FlowContextActions.result;

@Slf4j
@RequiredArgsConstructor
public class OneStepRunner<T> implements FlowRunner<T> {

    @Getter
    private final Flow<T> flow;

    @Override
    public void run(@NonNull Flow.State currentState) {
        log.debug("Executing flow for given state: {}", currentState);
        var sm = flow.getStateManager();
        var metadata = flow.getMetadata();
        var extendEnabled = (Boolean) metadata.getOrDefault(EXTEND_ENABLED, Boolean.FALSE);
        var flowContextRef = new AtomicReference<FlowContext<T>>(FlowContextActions.of(b -> b
                .state(currentState)
                .metadata(metadata)
                .flow(flow)
        ));

        var runnerContext = RunnerContext.<T>of(b -> b
                .flow(flow)
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
                        var aggregatedContext = pipe.aggregate(contexts);
                        processSinglePipe(runnerContext, -1, -1, ctx -> aggregatedContext, state);
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
            var extendContext = runnerContext.getFlowContext()
                    .copy(b -> b.flow(runnerContext.flowCopy(Function.identity())));
            runnerContext.updateFlowContext(runnerContext.executeExtendAction(extendContext));
        }

        var bootstrapContext = runnerContext.executeBootstrapAction(runnerContext.copyFlowContext(b -> b.currentItem(originItem)
                .flow(runnerContext.flowCopy(Function.identity()))));
        runnerContext.updateFlowContext(bootstrapContext);
    }

    private void processItem(RunnerContext<T> runnerContext, int itemIndex, int startPipeIndex) {
        var itemFromSource = runnerContext.getItem(itemIndex);
        var refreshContext = runnerContext.copyFlowContext(b -> b.currentItem(itemFromSource)
                .flow(runnerContext.flowCopy(Function.identity())));
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
            var aggregatedContext = windowedPipe.aggregate(contexts);

            // Process the aggregated result
            processSinglePipe(runnerContext, itemIndex, pipeIndex, ctx -> aggregatedContext, state);

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

    private void processSinglePipe(RunnerContext<T> runnerContext, int itemIndex, int pipeIndex,
                                   Function<FlowContext<T>, FlowContext<T>> pipe, Flow.State state) {
        var eachContext = runnerContext.executeAroundEachAction(
                runnerContext.getFlowContext().withCurrentPipeIndex(pipeIndex), pipe);

        var sinkEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_SINK));
        var stateUpdateEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_STATE_UPDATE));
        runnerContext.updateFlowContext(eachContext.enableChanges());

        if (sinkEnabled) {
            runnerContext.executeSinkAction(eachContext.copy(b -> b));
        }

        if (stateUpdateEnabled) {
            runnerContext.saveCurrentState(itemIndex, pipeIndex + 1, state);
        }
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

    @Override
    public FlowRunner<T> recreate(Flow<T> subFlow) {
        return new OneStepRunner<>(subFlow);
    }

    /* ============= */

    /**
     * Window definition to be used with aggregate operations
     */
    public sealed interface Window permits Window.SessionWindow, Window.SlidingWindow, Window.TumblingWindow {

        static Window tumbling(int size) {
            return new TumblingWindow(size);
        }

        static Window sliding(int size, int slide) {
            return new SlidingWindow(size, slide);
        }

        static Window session(int timeout) {
            return new SessionWindow(timeout);
        }

        int getSize();

        int getSlide();

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
     * Windowed pipe for processing data in windows
     */
    public interface WindowedPipe<T> extends Function<FlowContext<T>, FlowContext<T>> {

        String getWindowId();

        Window getWindow();

        boolean shouldTrigger(List<FlowContext<T>> contexts);

        boolean shouldClearAfterTrigger();

        FlowContext<T> aggregate(List<FlowContext<T>> contexts);

        @Override
        default FlowContext<T> apply(FlowContext<T> context) {
            // This is a placeholder - actual processing happens in OneStepRunner
            return context;
        }

        default boolean shouldUpdateState(List<FlowContext<T>> contexts) {
            return false;
        }

    }

    /**
     * Aggregation function to be used with windows
     */
    @FunctionalInterface
    public interface Aggregation<T> {

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

        static <T> Aggregation<T> count() {
            return contexts -> contexts.getLast().rearrange(FlowContext::resultArg, result((long) contexts.size()));
        }

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

        static <I, U> Aggregation<I> map(Function<FlowContext<I>, U> extractor) {
            return contexts -> {
                var result = contexts.stream()
                        .map(extractor)
                        .toList();

                return contexts.getLast().rearrange(FlowContext::resultArg, result(result));
            };
        }

        static <I, U> Aggregation<I> pack(Function<FlowContext<I>, Pack<I, U>> extractor) {
            return map(extractor);
        }

        static <T> Aggregation<T> items() {
            return map(FlowContext::getCurrentItem);
        }

        FlowContext<T> apply(List<FlowContext<T>> contexts);

        default Aggregation<T> andThen(Aggregation<T> after) {
            return contexts -> after.apply(List.of(apply(contexts)));
        }

        default Aggregation<T> onResult(Function<FlowContext<T>, FlowContext<T>> function) {
            return contexts -> {
                var context = apply(contexts);
                return context.optionalValue(FlowContext::resultArg)
                        .map(o -> function.apply(context))
                        .orElse(context);
            };
        }

    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    private static class RunnerContext<T> {

        Flow<T> flow;
        Flow.State currentState;
        StateManager sm;
        Map<String, Object> metadata;
        boolean extendEnabled;
        AtomicReference<FlowContext<T>> flowContextRef;
        WindowBuffer<T> windowBuffer;

        public void updateFlowContext(FlowContext<T> newContext) {
            flowContextRef.set(newContext);
        }

        public FlowContext<T> copyFlowContext(Function<FlowContext.FlowContextBuilder<T>, FlowContext.FlowContextBuilder<T>> fn) {
            return getFlowContext().copy(fn);
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

        public FlowContext<T> executeAroundEachAction(FlowContext<T> context, Function<FlowContext<T>, FlowContext<T>> action) {
            return Objects.requireNonNull(getFlow().getAroundEachAction().apply(context, action), "Context can't be null");
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
                    .orElseThrow(() -> new AppIllegalStateException("Window for given id not found", windowId));
        }

        public static <U> RunnerContext<U> of(Function<RunnerContextBuilder<U>, RunnerContextBuilder<U>> creator) {
            return creator.apply(builder()).build();
        }

    }

    /**
     * Buffer to store contexts for windowed operations
     */
    private static class WindowBuffer<T> {

        private final Map<String, List<FlowContext<T>>> windows = new ConcurrentHashMap<>();

        public void add(String windowId, FlowContext<T> context) {
            windows.computeIfAbsent(windowId, k -> new ArrayList<>()).add(context);
        }

        public List<FlowContext<T>> getWindow(String windowId) {
            return windows.getOrDefault(windowId, List.of());
        }

        public void clearWindow(String windowId) {
            windows.remove(windowId);
        }

        public void slideWindow(String windowId, int slideSize) {
            var window = windows.get(windowId);
            if (window != null && slideSize > 0 && slideSize <= window.size()) {
                windows.put(windowId, new ArrayList<>(window.subList(slideSize, window.size())));
            }
        }

        public Map<String, List<FlowContext<T>>> getAllWindows() {
            return new HashMap<>(windows);
        }

        public void clear() {
            windows.clear();
        }

    }

    /**
     * Base implementation of a windowed pipe
     */
    @Value
    @RequiredArgsConstructor
    public static class BaseWindowedPipe<T> implements WindowedPipe<T> {

        String windowId;
        Window window;
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
     * Extension methods for Flow to support windowing operations
     */
    public static class FlowExtensions {

        public static <T> WindowedPipe<T> window(String windowId, Window window, Aggregation<T> aggregation) {
            return new BaseWindowedPipe<>(windowId, window, aggregation);
        }

        public static <T> WindowedPipe<T> aggregate(Window window, Aggregation<T> aggregation) {
            String windowId = UUID.randomUUID().toString();
            return window(windowId, window, aggregation);
        }

    }

}
