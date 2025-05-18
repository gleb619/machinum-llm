package machinum.flow;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import machinum.flow.Flow.State;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static machinum.util.MockitoUtil.spyLambda;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowTest {

    private Flow<String> flowWithMocks;
    private Flow<String> flowWithState;
    private Consumer<FlowContext<String>> beforeAllMock;
    private Consumer<FlowContext<String>> afterAllMock;
    private BiConsumer<FlowContext<String>, Exception> exceptionActionMock;
    private BiFunction<FlowContext<String>, Function<FlowContext<String>, FlowContext<String>>, FlowContext<String>> aroundEachMock;
    private BiConsumer<FlowContext<String>, Runnable> aroundAllMock;
    private BiConsumer<FlowContext<String>, Runnable> aroundEachStateMock;
    private Function<FlowContext<String>, String> refreshMock;
    private Function<FlowContext<String>, FlowContext<String>> bootstrapMock;
    private Consumer<FlowContext<String>> sinkMock;
    private final TestObject testBean = TestObject.testObject("abc123");
    private OneStepRunner runnerForMocks;
    private OneStepRunner runnerForState;
    private RecursiveFlowRunner recursiveRunnerForState;
    private State currentStep;
    private State nextStep;
    private final TestStateManager testStateManager = new TestStateManager();
    private final List<String> log = new ArrayList<>();
    private AtomicInteger counter;


    @BeforeEach
    void setUp() {
        var stateManager = StateManager.create(
                testStateManager::saveState,
                testStateManager::getLastProcessedItem,
                testStateManager::getLastProcessorIndex,
                testStateManager::getState,
                (metadata, string1) -> testStateManager.saveFlowChunkState(string1),
                (metadata, string) -> testStateManager.getFlowChunkState(string));

        counter = new AtomicInteger();
        beforeAllMock = spyLambda(Consumer.class, (ctx -> {
            String msg = "%s before".formatted(ctx.getState());
            log.add(msg);
            System.out.println(msg);
        }));
        afterAllMock = spyLambda(Consumer.class, (ctx -> {
            String msg = "%s after".formatted(ctx.getState());
            log.add(msg);
            System.out.println(msg);
        }));
        exceptionActionMock = spyLambda(BiConsumer.class, ((ctx, e) -> {
            String msg = "%s exception, %s".formatted(ctx.getState(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);
        }));
        aroundEachMock = spyLambda(BiFunction.class, ((ctx, fn) -> {
            var result = fn.apply(ctx);
            var value = result.optionalValue(FlowContext::textArg).orElse("<null>");
            var msg = "%s invocation=%s, item=%s, result=%s".formatted(result.getState(), counter.get(), result.getCurrentItem(), value);
            log.add(msg);
            System.out.println(msg);

            return result;
        }));
        aroundAllMock = spyLambda(BiConsumer.class, ((ctx, run) -> run.run()));
        aroundEachStateMock = spyLambda(BiConsumer.class, ((ctx, run) -> run.run()));
        refreshMock = spyLambda(Function.class, ((ctx) -> {
            var currentItem = ctx.getCurrentItem();
            var msg = "%s refresh=%s, item=%s".formatted(ctx.getState(), counter.getAndIncrement(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);

            return currentItem;
        }));
        bootstrapMock = spyLambda(Function.class, ((ctx) -> {
            var msg = "%s bootstrap=%s, item=%s".formatted(ctx.getState(), counter.get(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);

            return ctx;
        }));
        sinkMock = spyLambda(Consumer.class, (ctx -> {
            String msg = "%s sink=%s, item=%s".formatted(ctx.getState(), counter.get(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);
        }));

        flowWithMocks = Flow.from(List.of("item1", "item2"))
                .refresh(refreshMock)
                .bootstrap(bootstrapMock)
                .beforeAll(beforeAllMock)
                .aroundEach(aroundEachMock)
                .aroundAll(aroundAllMock)
                .aroundEachState(aroundEachStateMock)
                .exception(exceptionActionMock)
                .afterAll(afterAllMock)
                .onState(TestState.STEP1)
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.testString())))
                .sink(sinkMock);

        flowWithState = flowWithMocks.withStateManager(stateManager)
                .onState(TestState.STEP2)
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.firstMethod())))
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.secondMethod())))
                .build();

        runnerForMocks = new OneStepRunner(flowWithMocks);
        runnerForState = new OneStepRunner(flowWithState);
        recursiveRunnerForState = new RecursiveFlowRunner<>(runnerForState);
        currentStep = TestState.STEP1;
        nextStep = TestState.STEP2;
    }

    @Test
    void testMainMethods_success() {
        runnerForMocks.run(currentStep);

        verify(beforeAllMock, times(1))
                .accept(any());

        verify(refreshMock, times(2))
                .apply(any());

        verify(aroundAllMock, times(1))
                .accept(any(), any());

        verify(aroundEachStateMock, times(2))
                .accept(any(), any());

        verify(aroundEachMock, times(2))
                .apply(any(), any());

        verify(afterAllMock, times(1))
                .accept(any());

        verify(sinkMock, times(2))
                .accept(any());

        verify(testBean, times(2))
                .testString();
    }

    @Test
    void testMainMethods_checkAroundEach() {
        var counter = new AtomicInteger();
        var flow = flowWithState.aroundEach((ctx, fn) -> {
            counter.getAndIncrement();
            return fn.apply(ctx);
        });

        new OneStepRunner(flow)
                .run(currentStep);

        verify(aroundEachMock, atLeast(2))
                .andThen(any());

        assertThat(counter)
                .hasValue(2);
    }

    @Test
    void testState_fail() {
        Assertions.assertThatThrownBy(() -> runnerForState.run(State.defaultState()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("At least one pipe must be present for handling");
    }

    @Test
    void testState_success() {
        testStateManager.setState(currentStep);
        runnerForState.run(currentStep);

        verify(beforeAllMock, times(1))
                .accept(any());

        verify(refreshMock, times(2))
                .apply(any());

        verify(aroundAllMock, times(1))
                .accept(any(), any());

        verify(aroundEachStateMock, times(2))
                .accept(any(), any());

        verify(aroundEachMock, times(2))
                .apply(any(), any());

        verify(afterAllMock, times(1))
                .accept(any());

        verify(sinkMock, times(2))
                .accept(any());

        verify(testBean, times(2))
                .testString();
    }

    @Test
    void testNextState_success() {
        testStateManager.setState(nextStep);
        runnerForState.run(nextStep);

        verify(beforeAllMock, times(1))
                .accept(any());

        verify(refreshMock, times(2))
                .apply(any());

        verify(aroundAllMock, times(1))
                .accept(any(), any());

        verify(aroundEachStateMock, times(2))
                .accept(any(), any());

        verify(aroundEachMock, times(4))
                .apply(any(), any());

        verify(afterAllMock, times(1))
                .accept(any());

        verify(sinkMock, times(4))
                .accept(any());

        verify(testBean, times(0))
                .testString();

        verify(testBean, times(2))
                .firstMethod();

        verify(testBean, times(2))
                .secondMethod();
    }

    @Test
    void testStateWithException_success() {
        when(testBean.testString())
                .thenReturn("abc123")
                .thenThrow(NullPointerException.class)
                .thenReturn("123abc");

        testStateManager.setState(currentStep);

        Assertions.assertThatThrownBy(() -> recursiveRunnerForState.run(currentStep))
                .hasCauseInstanceOf(NullPointerException.class)
                .isInstanceOf(RuntimeException.class);

        int processedItem = testStateManager.getLastProcessedItem();
        int lastProcessedPipe = testStateManager.getLastProcessedPipe();

        assertThat(List.of(processedItem, lastProcessedPipe))
                .isNotEmpty()
                .isEqualTo(List.of(1, 0));
    }

    @Test
    void testProceedStateWithException_success() {
        testStateWithException_success();

        recursiveRunnerForState.run(currentStep);

        int processedItem = testStateManager.getLastProcessedItem();
        int lastProcessedPipe = testStateManager.getLastProcessedPipe();

        assertThat(List.of(processedItem, lastProcessedPipe))
                .isNotEmpty()
                .isEqualTo(List.of(2, 0));

        assertThat(String.join("\n", log))
                .isEqualTo("""
                        STEP1 before
                        STEP1 bootstrap=0, item=item1
                        STEP1 refresh=0, item=item1
                        STEP1 invocation=1, item=item1, result=abc123
                        STEP1 sink=1, item=item1
                        STEP1 refresh=1, item=item2
                        STEP1 exception, item2
                        STEP1 after
                        STEP1 before
                        STEP1 bootstrap=2, item=item2
                        STEP1 refresh=2, item=item2
                        STEP1 invocation=3, item=item2, result=123abc
                        STEP1 sink=3, item=item2
                        STEP1 after
                        STEP2 before
                        STEP2 bootstrap=3, item=item1
                        STEP2 refresh=3, item=item1
                        STEP2 invocation=4, item=item1, result=abc123-2
                        STEP2 sink=4, item=item1
                        STEP2 invocation=4, item=item1, result=abc123-3
                        STEP2 sink=4, item=item1
                        STEP2 refresh=4, item=item2
                        STEP2 invocation=5, item=item2, result=abc123-2
                        STEP2 sink=5, item=item2
                        STEP2 invocation=5, item=item2, result=abc123-3
                        STEP2 sink=5, item=item2
                        STEP2 after""");

        assertThat(counter.get())
                .isEqualTo(5);
    }

    enum TestState implements State {

        STEP1,
        STEP2,

    }

    @RequiredArgsConstructor
    static class TestObject {

        private final String text;

        public static TestObject testObject(String text) {
            return spy(new TestObject(text));
        }

        public String testString() {
            return text + "-1";
        }

        public String firstMethod() {
            return text + "-2";
        }

        public String secondMethod() {
            return text + "-3";
        }

    }

    @Data
    public static class TestStateManager {

        int lastProcessedItem = 0;
        int lastProcessedPipe = 0;
        State state = State.defaultState();
        boolean chunkProcessed = false;

        public int getLastProcessedItem(Map<String, Object> metadata) {
            return lastProcessedItem;
        }

        public int getLastProcessorIndex(Map<String, Object> metadata) {
            return lastProcessedPipe;
        }

        public State getState(Map<String, Object> metadata) {
            return state;
        }

        public void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, State state) {
            setLastProcessedItem(itemIndex);
            setLastProcessedPipe(pipeIndex);
            setState(state);
        }

        public void saveFlowChunkState(String string) {
            chunkProcessed = Objects.nonNull(string);
        }

        public boolean getFlowChunkState(String string) {
            return chunkProcessed;
        }

    }

}
