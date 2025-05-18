package machinum.flow;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
        var context = new AtomicReference<FlowContext<T>>(FlowContextActions.of(currentState, metadata));

        executeFlow(currentState, sm, metadata, extendEnabled, context);
    }

    private void executeFlow(Flow.State currentState, StateManager sm, Map<String, Object> metadata,
                             boolean extendEnabled, AtomicReference<FlowContext<T>> context) {
        flow.getBeforeAllAction().accept(context.get());

        try {
            flow.getAroundAllAction().accept(context.get(), () ->
                    processItems(sm, metadata, extendEnabled, context, currentState));
        } finally {
            flow.getAfterAllAction().accept(context.get());
            log.debug("Flow has been executed for given state: {}", currentState);
        }
    }

    private void processItems(StateManager sm, Map<String, Object> metadata, boolean extendEnabled,
                              AtomicReference<FlowContext<T>> context, Flow.State currentState) {
        var currentItemIndex = sm.getLastProcessedItem(metadata);
        var currentPipeIndex = sm.getLastProcessorIndex(metadata);
        var originItem = flow.getSource().get(currentItemIndex);

        prepareContext(extendEnabled, context, originItem);

        for (int i = currentItemIndex; i < flow.getSource().size(); i++) {
            processItem(i, sm, metadata, context, currentPipeIndex);
        }

        updateNextState(sm, metadata, currentState);
    }

    private void prepareContext(boolean extendEnabled, AtomicReference<FlowContext<T>> context, T originItem) {
        if (extendEnabled) {
            var extendContext = context.get()
                    .copy(b -> b.flow(flow.copy(Function.identity())));
            context.set(flow.getExtendAction().apply(extendContext));
        }

        var bootstrapContext = flow.getBootstrapAction().apply(context.get().copy(b -> b.currentItem(originItem)
                .flow(flow.copy(Function.identity()))));
        context.set(bootstrapContext);
    }

    private void processItem(int itemIndex, StateManager sm, Map<String, Object> metadata,
                             AtomicReference<FlowContext<T>> context, int startPipeIndex) {
        var itemFromSource = flow.getSource().get(itemIndex);
        var refreshContext = context.get().copy(b -> b.currentItem(itemFromSource)
                .flow(flow.copy(Function.identity())));
        var itemAfterRefresh = flow.getRefreshAction().apply(refreshContext);
        var currentItemContext = refreshContext.withCurrentItem(itemAfterRefresh)
                .rearrange(FlowContext::iterationArg, iteration(itemIndex + 1));
        context.set(currentItemContext);

        var state = currentItemContext.getState();
        var pipes = Objects.requireNonNull(flow.getStatePipes().get(state),
                "At least one pipe must be present for handling: " + state);

        flow.getAroundEachStateAction().accept(currentItemContext, () ->
                processPipes(itemIndex, startPipeIndex, metadata, pipes, context, state, sm));

        sm.saveState(metadata, itemIndex + 1, 0, state);
    }

    private void processPipes(int itemIndex, int startPipeIndex, Map<String, Object> metadata,
                              List<Function<FlowContext<T>, FlowContext<T>>> pipes,
                              AtomicReference<FlowContext<T>> context, Flow.State state, StateManager sm) {
        for (int j = startPipeIndex; j < pipes.size(); j++) {
            try {
                processSinglePipe(itemIndex, j, pipes.get(j), metadata, context, state, sm);
            } catch (Exception e) {
                handlePipeException(context, e);
            }
        }
    }

    private void processSinglePipe(int itemIndex, int pipeIndex, Function<FlowContext<T>, FlowContext<T>> pipe,
                                   Map<String, Object> metadata, AtomicReference<FlowContext<T>> context, Flow.State state, StateManager sm) {
        var eachContext = Objects.requireNonNull(flow.getAroundEachAction().apply(context.get().withCurrentPipeIndex(pipeIndex), pipe),
                "Context can't be null");

        var sinkEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_SINK));
        var stateUpdateEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_STATE_UPDATE));
        context.set(eachContext.enableChanges());

        if (sinkEnabled) {
            flow.getSinkAction().accept(eachContext.copy(b -> b.flow(flow)));
        }

        if (stateUpdateEnabled) {
            sm.saveState(metadata, itemIndex, pipeIndex + 1, state);
        }
    }

    private void handlePipeException(AtomicReference<FlowContext<T>> context, Exception e) {
        try {
            flow.getExceptionAction().accept(context.get(), e);
        } catch (Exception ex) {
            //ignore
        }
        flow.getErrorStrategy().handleError(context.get(), e);
    }

    private void updateNextState(StateManager sm, Map<String, Object> metadata, Flow.State currentState) {
        var nextState = flow.nextState(currentState);
        if (Objects.nonNull(nextState)) {
            sm.saveState(metadata, 0, 0, nextState);
        }
    }

    @Override
    public FlowRunner<T> recreate(Flow<T> subFlow) {
        return new OneStepRunner<>(subFlow);
    }

}
