package machinum.flow;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static machinum.config.Constants.*;
import static machinum.flow.FlowContextActions.iteration;

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
                .flowContextRef(flowContextRef));
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

        updateNextState(runnerContext);
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

        runnerContext.executeAroundEachStateAction(() -> processPipes(runnerContext, itemIndex, startPipeIndex, pipes, state));

        runnerContext.saveCurrentState(itemIndex + 1, 0, state);
    }

    private void processPipes(RunnerContext<T> runnerContext, int itemIndex, int startPipeIndex,
                              List<Function<FlowContext<T>, FlowContext<T>>> pipes, Flow.State state) {
        for (int j = startPipeIndex; j < pipes.size(); j++) {
            try {
                processSinglePipe(runnerContext, itemIndex, j, pipes.get(j), state);
            } catch (Exception e) {
                handlePipeException(runnerContext, e);
            }
        }
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

        public void updateFlowContext(FlowContext<T> newContext) {
            flowContextRef.set(newContext);
        }

        public FlowContext<T> copyFlowContext(Function<FlowContext.FlowContextBuilder<T>, FlowContext.FlowContextBuilder<T>> fn) {
            return getFlowContext().copy(fn);
        }

        public FlowContext<T> getFlowContext() {
            return flowContextRef.get();
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

        public void executeAroundEachStateAction(Runnable action) {
            getFlow().getAroundEachStateAction().accept(getFlowContext(), action);
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

        public static <U> RunnerContext<U> of(Function<RunnerContextBuilder<U>, RunnerContextBuilder<U>> creator) {
            return creator.apply(builder()).build();
        }

    }

}
