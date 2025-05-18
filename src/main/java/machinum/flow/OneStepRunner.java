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
        var flowContextRef = new AtomicReference<FlowContext<T>>(FlowContextActions.of(currentState, metadata));

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
        runnerContext.getFlow().getBeforeAllAction().accept(runnerContext.getFlowContextRef().get());

        try {
            runnerContext.getFlow().getAroundAllAction().accept(runnerContext.getFlowContextRef().get(), () ->
                    processItems(runnerContext));
        } finally {
            runnerContext.getFlow().getAfterAllAction().accept(runnerContext.getFlowContextRef().get());
            log.debug("Flow has been executed for given state: {}", runnerContext.getCurrentState());
        }
    }

    private void processItems(RunnerContext<T> runnerContext) {
        var currentItemIndex = runnerContext.getSm().getLastProcessedItem(runnerContext.getMetadata());
        var currentPipeIndex = runnerContext.getSm().getLastProcessorIndex(runnerContext.getMetadata());
        var originItem = runnerContext.getFlow().getSource().get(currentItemIndex);

        prepareContext(runnerContext, originItem);

        for (int i = currentItemIndex; i < runnerContext.getFlow().getSource().size(); i++) {
            processItem(runnerContext, i, currentPipeIndex);
        }

        updateNextState(runnerContext);
    }

    private void prepareContext(RunnerContext<T> runnerContext, T originItem) {
        if (runnerContext.isExtendEnabled()) {
            var extendContext = runnerContext.getFlowContextRef().get()
                    .copy(b -> b.flow(runnerContext.getFlow().copy(Function.identity())));
            runnerContext.getFlowContextRef().set(runnerContext.getFlow().getExtendAction().apply(extendContext));
        }

        var bootstrapContext = runnerContext.getFlow().getBootstrapAction().apply(runnerContext.getFlowContextRef().get().copy(b -> b.currentItem(originItem)
                .flow(runnerContext.getFlow().copy(Function.identity()))));
        runnerContext.getFlowContextRef().set(bootstrapContext);
    }

    private void processItem(RunnerContext<T> runnerContext, int itemIndex, int startPipeIndex) {
        var itemFromSource = runnerContext.getFlow().getSource().get(itemIndex);
        var refreshContext = runnerContext.getFlowContextRef().get().copy(b -> b.currentItem(itemFromSource)
                .flow(runnerContext.getFlow().copy(Function.identity())));
        var itemAfterRefresh = runnerContext.getFlow().getRefreshAction().apply(refreshContext);
        var currentItemContext = refreshContext.withCurrentItem(itemAfterRefresh)
                .rearrange(FlowContext::iterationArg, iteration(itemIndex + 1)); // Assuming iteration() is a static helper
        runnerContext.getFlowContextRef().set(currentItemContext);

        var state = currentItemContext.getState();
        var pipes = Objects.requireNonNull(runnerContext.getFlow().getStatePipes().get(state),
                "At least one pipe must be present for handling: " + state);

        runnerContext.getFlow().getAroundEachStateAction().accept(currentItemContext, () ->
                processPipes(runnerContext, itemIndex, startPipeIndex, pipes, state));

        runnerContext.getSm().saveState(runnerContext.getMetadata(), itemIndex + 1, 0, state);
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
        var eachContext = Objects.requireNonNull(runnerContext.getFlow().getAroundEachAction().apply(runnerContext.getFlowContextRef().get().withCurrentPipeIndex(pipeIndex), pipe),
                "Context can't be null");

        var sinkEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_SINK));
        var stateUpdateEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_STATE_UPDATE));
        runnerContext.getFlowContextRef().set(eachContext.enableChanges());

        if (sinkEnabled) {
            runnerContext.getFlow().getSinkAction().accept(eachContext.copy(b -> b.flow(runnerContext.getFlow())));
        }

        if (stateUpdateEnabled) {
            runnerContext.getSm().saveState(runnerContext.getMetadata(), itemIndex, pipeIndex + 1, state);
        }
    }

    private void handlePipeException(RunnerContext<T> runnerContext, Exception e) {
        try {
            runnerContext.getFlow().getExceptionAction().accept(runnerContext.getFlowContextRef().get(), e);
        } catch (Exception ex) {
            //ignore
        }
        runnerContext.getFlow().getErrorStrategy().handleError(runnerContext.getFlowContextRef().get(), e);
    }

    private void updateNextState(RunnerContext<T> runnerContext) {
        var nextState = runnerContext.getFlow().nextState(runnerContext.getCurrentState());
        if (Objects.nonNull(nextState)) {
            runnerContext.getSm().saveState(runnerContext.getMetadata(), 0, 0, nextState);
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

        public static <U> RunnerContext<U> of(Function<RunnerContextBuilder<U>, RunnerContextBuilder<U>> creator) {
            return creator.apply(builder()).build();
        }

    }

}
