package machinum.flow;

import machinum.flow.Flow.ErrorStrategy;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class OneStepRunnerTest {

    private Flow<String> flow;

    @Mock
    private StateManager stateManager;

    @Mock
    private Consumer<FlowContext<String>> beforeAllAction;

    @Mock
    private BiConsumer<FlowContext<String>, Runnable> aroundAllAction;

    @Mock
    private Consumer<FlowContext<String>> afterAllAction;

    @Mock
    private Function<FlowContext<String>, String> refreshAction;

    @Mock
    private Function<FlowContext<String>, FlowContext<String>> extendAction;

    @Mock
    private Function<FlowContext<String>, FlowContext<String>> bootstrapAction;

    @Mock
    private BiConsumer<FlowContext<String>, Runnable> aroundEachStateAction;

    @Mock
    private BiFunction<FlowContext<String>, Function<FlowContext<String>, FlowContext<String>>, FlowContext<String>> aroundEachAction;

    @Mock
    private Consumer<FlowContext<String>> sinkAction;

    @Mock
    private BiConsumer<FlowContext<String>, Exception> exceptionAction;

    @Mock
    private ErrorStrategy<String> errorStrategy;

    @Mock
    private List<String> source;

    @Mock
    private Flow.State currentState;

    @Mock
    private Flow.State nextState;

    private Map<String, Object> metadata;
    private Map<Flow.State, List<Function<FlowContext<String>, FlowContext<String>>>> statePipes;
    private List<Function<FlowContext<String>, FlowContext<String>>> pipes;
    private OneStepRunner<String> runner;

    @BeforeEach
    void setUp() {
        flow = Flow.from(List.of("1", "2", "3"));

        metadata = new HashMap<>();
        metadata.put("EXTEND_ENABLED", Boolean.TRUE);

        pipes = Arrays.asList(
                mock(Function.class),
                mock(Function.class)
        );

        statePipes = new HashMap<>();
        statePipes.put(currentState, pipes);

        // Configure flow mock
        when(flow.getStateManager()).thenReturn(stateManager);
        when(flow.getMetadata()).thenReturn(metadata);
        when(flow.getBeforeAllAction()).thenReturn(beforeAllAction);
        when(flow.getAroundAllAction()).thenReturn(aroundAllAction);
        when(flow.getAfterAllAction()).thenReturn(afterAllAction);
        when(flow.getRefreshAction()).thenReturn(refreshAction);
        when(flow.getExtendAction()).thenReturn(extendAction);
        when(flow.getBootstrapAction()).thenReturn(bootstrapAction);
        when(flow.getAroundEachStateAction()).thenReturn(aroundEachStateAction);
        when(flow.getAroundEachAction()).thenReturn(aroundEachAction);
        when(flow.getSinkAction()).thenReturn(sinkAction);
        when(flow.getExceptionAction()).thenReturn(exceptionAction);
        when(flow.getErrorStrategy()).thenReturn(errorStrategy);
        when(flow.getSource()).thenReturn(source);
        when(flow.getStatePipes()).thenReturn(statePipes);
        when(flow.nextState(currentState)).thenReturn(nextState);
        when(flow.copy(any())).thenReturn(flow);

        // Execute aroundAllAction when invoked with a Runnable
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(aroundAllAction).accept(any(), any(Runnable.class));

        // Execute aroundEachStateAction when invoked with a Runnable
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(aroundEachStateAction).accept(any(), any(Runnable.class));

        runner = new OneStepRunner<>(flow);
    }

    @Test
    void shouldExecuteFlowForNormalOperation() {
        // Given
        when(stateManager.getLastProcessedItem(metadata)).thenReturn(0);
        when(stateManager.getLastProcessorIndex(metadata)).thenReturn(0);
        when(source.size()).thenReturn(2);
        when(source.get(0)).thenReturn("item1");
        when(source.get(1)).thenReturn("item2");

        FlowContext<String> mockContext = mock(FlowContext.class);
        when(FlowContextActions.<String>of(eq(currentState), eq(metadata))).thenReturn(mockContext);
        when(mockContext.getState()).thenReturn(currentState);
        when(mockContext.copy(any())).thenReturn(mockContext);
        when(mockContext.withCurrentItem(any())).thenReturn(mockContext);
        when(mockContext.withCurrentPipeIndex(anyInt())).thenReturn(mockContext);
        when(mockContext.rearrange(any(Function.class), any(FlowArgument.class))).thenReturn(mockContext);
        when(mockContext.enableChanges()).thenReturn(mockContext);
        when(mockContext.metadata(anyString())).thenReturn(null);

        when(bootstrapAction.apply(any())).thenReturn(mockContext);
        when(extendAction.apply(any())).thenReturn(mockContext);
        when(refreshAction.apply(any())).thenReturn("refreshed");
        when(aroundEachAction.apply(any(), any())).thenReturn(mockContext);

        // When
        runner.run(currentState);

        // Then
        verify(beforeAllAction).accept(any());
        verify(aroundAllAction).accept(any(), any());
        verify(bootstrapAction).apply(any());
        verify(extendAction).apply(any());
        verify(refreshAction, times(2)).apply(any());
        verify(aroundEachStateAction, times(2)).accept(any(), any());
        verify(aroundEachAction, times(4)).apply(any(), any());
        verify(sinkAction, times(4)).accept(any());
        verify(stateManager, times(6)).saveState(any(), anyInt(), anyInt(), any());
        verify(afterAllAction).accept(any());
    }

    @Test
    void shouldHandleExceptionInPipe() {
        // Given
        when(stateManager.getLastProcessedItem(metadata)).thenReturn(0);
        when(stateManager.getLastProcessorIndex(metadata)).thenReturn(0);
        when(source.size()).thenReturn(1);
        when(source.get(0)).thenReturn("item1");

        FlowContext<String> mockContext = mock(FlowContext.class);
        when(FlowContextActions.<String>of(eq(currentState), eq(metadata))).thenReturn(mockContext);
        when(mockContext.getState()).thenReturn(currentState);
        when(mockContext.copy(any())).thenReturn(mockContext);
        when(mockContext.withCurrentItem(any())).thenReturn(mockContext);
        when(mockContext.withCurrentPipeIndex(anyInt())).thenReturn(mockContext);
        when(mockContext.rearrange(any(Function.class), any(FlowArgument.class))).thenReturn(mockContext);

        when(bootstrapAction.apply(any())).thenReturn(mockContext);
        when(extendAction.apply(any())).thenReturn(mockContext);
        when(refreshAction.apply(any())).thenReturn("refreshed");

        Exception pipeException = new RuntimeException("Pipe error");
        when(aroundEachAction.apply(any(), any())).thenThrow(pipeException);

        // When
        runner.run(currentState);

        // Then
        verify(exceptionAction).accept(eq(mockContext), eq(pipeException));
        verify(errorStrategy).handleError(eq(mockContext), eq(pipeException));
    }

    @Test
    void shouldSkipSinkWhenPreventSinkIsTrue() {
        // Given
        when(stateManager.getLastProcessedItem(metadata)).thenReturn(0);
        when(stateManager.getLastProcessorIndex(metadata)).thenReturn(0);
        when(source.size()).thenReturn(1);
        when(source.get(0)).thenReturn("item1");

        FlowContext<String> mockContext = mock(FlowContext.class);
        when(FlowContextActions.<String>of(eq(currentState), eq(metadata))).thenReturn(mockContext);
        when(mockContext.getState()).thenReturn(currentState);
        when(mockContext.copy(any())).thenReturn(mockContext);
        when(mockContext.withCurrentItem(any())).thenReturn(mockContext);
        when(mockContext.withCurrentPipeIndex(anyInt())).thenReturn(mockContext);
        when(mockContext.rearrange(any(Function.class), any(FlowArgument.class))).thenReturn(mockContext);
        when(mockContext.enableChanges()).thenReturn(mockContext);
        when(mockContext.metadata("PREVENT_SINK")).thenReturn(Boolean.TRUE);
        when(mockContext.metadata("PREVENT_STATE_UPDATE")).thenReturn(null);

        when(bootstrapAction.apply(any())).thenReturn(mockContext);
        when(extendAction.apply(any())).thenReturn(mockContext);
        when(refreshAction.apply(any())).thenReturn("refreshed");
        when(aroundEachAction.apply(any(), any())).thenReturn(mockContext);

        // When
        runner.run(currentState);

        // Then
        verify(sinkAction, never()).accept(any());
        verify(stateManager, times(3)).saveState(any(), anyInt(), anyInt(), any());
    }

    @Test
    void shouldSkipStateUpdateWhenPreventStateUpdateIsTrue() {
        // Given
        when(stateManager.getLastProcessedItem(metadata)).thenReturn(0);
        when(stateManager.getLastProcessorIndex(metadata)).thenReturn(0);
        when(source.size()).thenReturn(1);
        when(source.get(0)).thenReturn("item1");

        FlowContext<String> mockContext = mock(FlowContext.class);
        when(FlowContextActions.<String>of(eq(currentState), eq(metadata))).thenReturn(mockContext);
        when(mockContext.getState()).thenReturn(currentState);
        when(mockContext.copy(any())).thenReturn(mockContext);
        when(mockContext.withCurrentItem(any())).thenReturn(mockContext);
        when(mockContext.withCurrentPipeIndex(anyInt())).thenReturn(mockContext);
        when(mockContext.rearrange(any(Function.class), any(FlowArgument.class))).thenReturn(mockContext);
        when(mockContext.enableChanges()).thenReturn(mockContext);
        when(mockContext.metadata("PREVENT_SINK")).thenReturn(null);
        when(mockContext.metadata("PREVENT_STATE_UPDATE")).thenReturn(Boolean.TRUE);

        when(bootstrapAction.apply(any())).thenReturn(mockContext);
        when(extendAction.apply(any())).thenReturn(mockContext);
        when(refreshAction.apply(any())).thenReturn("refreshed");
        when(aroundEachAction.apply(any(), any())).thenReturn(mockContext);

        // When
        runner.run(currentState);

        // Then
        verify(sinkAction, times(2)).accept(any());
        verify(stateManager, times(1)).saveState(any(), anyInt(), anyInt(), any()); // Only for the state after item processing
    }

    @Test
    void shouldRecreateRunnerWithNewFlow() {
        // Given
        Flow<String> newFlow = mock(Flow.class);

        // When
        FlowRunner<String> newRunner = runner.recreate(newFlow);

        // Then
        assertEquals(OneStepRunner.class, newRunner.getClass());
        assertEquals(newFlow, ((OneStepRunner<String>) newRunner).getFlow());
    }

}