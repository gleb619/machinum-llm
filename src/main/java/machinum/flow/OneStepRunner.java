package machinum.flow;

import machinum.util.JavaUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static machinum.config.Constants.*;
import static machinum.flow.FlowContext.iteration;

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
        var context = new AtomicReference<>((FlowContext<T>) FlowContext.of(currentState, metadata));

        flow.getBeforeAllAction().accept(context.get());

        try {
            flow.getAroundAllAction().accept(context.get(), () -> {
                var currentItemIndex = sm.getLastProcessedItem(metadata);
                var currentPipeIndex = sm.getLastProcessorIndex(metadata);
                var originItem = flow.getSource().get(currentItemIndex);
                if (extendEnabled) {
                    var extendContext = context.get()
                            .copy(b -> b.flow(flow.copy(Function.identity())));
                    context.set(flow.getExtendAction().apply(extendContext));
                }
                var bootstrapContext = flow.getBootstrapAction().apply(context.get().copy(b -> b.currentItem(originItem)
                        .flow(flow.copy(Function.identity()))
                ));
                context.set(bootstrapContext);

                for (int i = currentItemIndex; i < flow.getSource().size(); i++) {
                    var itemFromSource = flow.getSource().get(i);
                    var refreshContext = context.get().copy(b -> b.currentItem(itemFromSource)
                            .flow(flow.copy(Function.identity())));
                    var itemAfterRefresh = flow.getRefreshAction().apply(refreshContext);
                    var currentItemContext = refreshContext.withCurrentItem(itemAfterRefresh)
                            .rearrange(FlowContext::iterationArg, iteration(i + 1));
                    context.set(currentItemContext);

                    var state = currentItemContext.getState();
                    var pipes = Objects.requireNonNull(flow.getStatePipes().get(state), "At least one pipe must be present for handling: " + state);
                    var indexOfItem = i;

                    flow.getAroundEachStateAction().accept(currentItemContext, () -> {
                        for (int j = currentPipeIndex; j < pipes.size(); j++) {
                            try {
                                var pipe = pipes.get(j);
                                var eachContext = Objects.requireNonNull(flow.getAroundEachAction().apply(context.get().withCurrentPipeIndex(j), pipe),
                                        "Context can't be null");

                                var sinkEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_SINK));
                                var stateUpdateEnabled = !Boolean.TRUE.equals(eachContext.metadata(PREVENT_STATE_UPDATE));
                                context.set(eachContext.enableChanges());

                                if (sinkEnabled) {
                                    flow.getSinkAction().accept(eachContext.copy(b -> b.flow(flow)));
                                }

                                if (stateUpdateEnabled) {
                                    sm.saveState(metadata, indexOfItem, j + 1, state);
                                }
                            } catch (Exception e) {
                                try {
                                    flow.getExceptionAction().accept(context.get(), e);
                                } catch (Exception ex) {
                                    //ignore
                                }
                                flow.getErrorStrategy().handleError(e, context.get());
                            }
                        }
                    });

                    sm.saveState(metadata, i + 1, 0, state);
                }

                var nextState = flow.nextState(currentState);
                if (Objects.nonNull(nextState)) {
                    sm.saveState(metadata, 0, 0, nextState);
                }

            });
        } finally {
            flow.getAfterAllAction().accept(context.get());
            log.debug("Flow has been executed for given state: {}", currentState);
        }
    }

    @Override
    public FlowRunner<T> recreate(Flow<T> subFlow) {
        return new OneStepRunner<>(subFlow);
    }

}
