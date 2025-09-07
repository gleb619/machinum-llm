package machinum.flow;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import machinum.flow.core.StateManager;
import machinum.flow.model.Flow;
import machinum.flow.model.Flow.State;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.flow.runner.OneStepRunner;
import machinum.flow.runner.RecursiveFlowRunner;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
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

import static machinum.flow.runner.OneStepRunner.Window.tumbling;

@ExtendWith(MockitoExtension.class)
class CommonFlowTest {

    private final TestObject testBean = TestObject.testObject("abc123");
    private final TestStateManager testStateManager = new TestStateManager();
    private final List<String> log = new ArrayList<>();
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
    private OneStepRunner runnerForMocks;
    private OneStepRunner runnerForState;
    private RecursiveFlowRunner recursiveRunnerForState;
    private State currentStep;
    private State nextStep;
    private AtomicInteger counter;
    private List<String> collection;


    @BeforeEach
    void setUp() {
        counter = new AtomicInteger();
        beforeAllMock = FlowTestUtil.spyLambda(Consumer.class, (ctx -> {
            String msg = "%s before".formatted(ctx.getState());
            log.add(msg);
            System.out.println(msg);
        }));
        afterAllMock = FlowTestUtil.spyLambda(Consumer.class, (ctx -> {
            String msg = "%s after".formatted(ctx.getState());
            log.add(msg);
            System.out.println(msg);
        }));
        exceptionActionMock = FlowTestUtil.spyLambda(BiConsumer.class, ((ctx, e) -> {
            String msg = "%s exception, %s".formatted(ctx.getState(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);
        }));
        aroundEachMock = FlowTestUtil.spyLambda(BiFunction.class, ((ctx, fn) -> {
            var result = fn.apply(ctx);
            var value = result.optionalValue(FlowContext::textArg).orElse("<null>");
            var msg = "%s invocation=%s, item=%s, result=%s".formatted(result.getState(), counter.get(), result.getCurrentItem(), value);
            log.add(msg);
            System.out.println(msg);

            return result;
        }));
        aroundAllMock = FlowTestUtil.spyLambda(BiConsumer.class, ((ctx, run) -> run.run()));
        aroundEachStateMock = FlowTestUtil.spyLambda(BiConsumer.class, ((ctx, run) -> run.run()));
        refreshMock = FlowTestUtil.spyLambda(Function.class, ((ctx) -> {
            var currentItem = ctx.getCurrentItem();
            var msg = "%s refresh=%s, item=%s".formatted(ctx.getState(), counter.getAndIncrement(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);

            return currentItem;
        }));
        bootstrapMock = FlowTestUtil.spyLambda(Function.class, ((ctx) -> {
            var msg = "%s bootstrap=%s, item=%s".formatted(ctx.getState(), counter.get(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);

            return ctx;
        }));
        sinkMock = FlowTestUtil.spyLambda(Consumer.class, (ctx -> {
            String msg = "%s sink=%s, item=%s".formatted(ctx.getState(), counter.get(), ctx.getCurrentItem());
            log.add(msg);
            System.out.println(msg);
        }));

        collection = List.of("item1", "item2");
        flowWithMocks = Flow.from(collection)
                .refresh(refreshMock)
                .bootstrap(bootstrapMock)
                .beforeAll(beforeAllMock)
                .aroundEach(aroundEachMock)
                .aroundAll(aroundAllMock)
                .aroundEachState(aroundEachStateMock)
                .exception(exceptionActionMock)
                .afterAll(afterAllMock)
                .onState(TestState.STEP1)
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.firstString())))
                .sink(sinkMock);

        flowWithState = flowWithMocks.withStateManager(testStateManager)
                .onState(TestState.STEP2)
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.secondMethod())))
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.thirdMethod())))
                .build();

        runnerForMocks = new OneStepRunner(flowWithMocks);
        runnerForState = new OneStepRunner(flowWithState);
        recursiveRunnerForState = new RecursiveFlowRunner<>(runnerForState, Runnable::run);
        currentStep = TestState.STEP1;
        nextStep = TestState.STEP2;
    }

    @Test
    void testMainMethods_success() {
        runnerForMocks.run(currentStep);

        Mockito.verify(beforeAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any());

        Mockito.verify(refreshMock, Mockito.times(2))
                .apply(ArgumentMatchers.any());

        Mockito.verify(aroundAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(aroundEachStateMock, Mockito.times(2))
                .accept(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(aroundEachMock, Mockito.times(2))
                .apply(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(afterAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any());

        Mockito.verify(sinkMock, Mockito.times(2))
                .accept(ArgumentMatchers.any());

        Mockito.verify(testBean, Mockito.times(2))
                .firstString();
    }

    /**
     * temporarily disabled, due complexity of stream api execution
     */
    @Test
    @Disabled
    void testMainMethods_checkAroundEach() {
        var counter = new AtomicInteger();
        var flow = flowWithState.aroundEach((ctx, fn) -> {
            counter.getAndIncrement();
            return fn.apply(ctx);
        });

        new OneStepRunner(flow)
                .run(currentStep);

        Mockito.verify(aroundEachMock, Mockito.atLeast(1))
                .andThen(ArgumentMatchers.any());

        Assertions.assertThat(counter)
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

        Mockito.verify(beforeAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any());

        Mockito.verify(refreshMock, Mockito.times(2))
                .apply(ArgumentMatchers.any());

        Mockito.verify(aroundAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(aroundEachStateMock, Mockito.times(2))
                .accept(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(aroundEachMock, Mockito.times(2))
                .apply(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(afterAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any());

        Mockito.verify(sinkMock, Mockito.times(2))
                .accept(ArgumentMatchers.any());

        Mockito.verify(testBean, Mockito.times(2))
                .firstString();
    }

    @Test
    void testNextState_success() {
        testStateManager.setState(nextStep);
        runnerForState.run(nextStep);

        Mockito.verify(beforeAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any());

        Mockito.verify(refreshMock, Mockito.times(2))
                .apply(ArgumentMatchers.any());

        Mockito.verify(aroundAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(aroundEachStateMock, Mockito.times(2))
                .accept(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(aroundEachMock, Mockito.times(4))
                .apply(ArgumentMatchers.any(), ArgumentMatchers.any());

        Mockito.verify(afterAllMock, Mockito.times(1))
                .accept(ArgumentMatchers.any());

        Mockito.verify(sinkMock, Mockito.times(4))
                .accept(ArgumentMatchers.any());

        Mockito.verify(testBean, Mockito.times(0))
                .firstString();

        Mockito.verify(testBean, Mockito.times(2))
                .secondMethod();

        Mockito.verify(testBean, Mockito.times(2))
                .thirdMethod();
    }

    @Test
    void testStateWithException_success() {
        Mockito.when(testBean.firstString())
                .thenReturn("abc123")
                .thenThrow(NullPointerException.class)
                .thenReturn("123abc");

        testStateManager.setState(currentStep);

        Assertions.assertThatThrownBy(() -> recursiveRunnerForState.run(currentStep))
                .hasCauseInstanceOf(NullPointerException.class)
                .isInstanceOf(RuntimeException.class);

        int processedItem = testStateManager.getLastProcessedItem();
        int lastProcessedPipe = testStateManager.getLastProcessedPipe();

        Assertions.assertThat(List.of(processedItem, lastProcessedPipe))
                .isNotEmpty()
                .isEqualTo(List.of(1, 0));
    }

    @Test
    void testProceedStateWithException_success() {
        testStateWithException_success();

        recursiveRunnerForState.run(currentStep);

        int processedItem = testStateManager.getLastProcessedItem();
        int lastProcessedPipe = testStateManager.getLastProcessedPipe();

        Assertions.assertThat(List.of(processedItem, lastProcessedPipe))
                .isNotEmpty()
                .isEqualTo(List.of(2, 0));

        Assertions.assertThat(String.join("\n", log))
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

        Assertions.assertThat(counter.get())
                .isEqualTo(5);
    }

    @Test
    void testWindows_success() {
        var flowWindows = flowWithMocks.copy(b -> b)
                .onState(TestState.STEP2)
                .pipe(ctx -> ctx.addArgs(FlowContextActions.text(testBean.secondMethod())))
                .window(tumbling(collection.size()), contexts -> {
                    var output = testBean.customMethod("w-%s".formatted(contexts.size()));
                    var arg = FlowContextActions.text(output);
                    var result = contexts.getLast().addArgs(arg);
                    return result;
                })
                .build();

        var runner = new OneStepRunner<>(flowWindows);

        runner.run(nextStep);

        Assertions.assertThat(String.join("\n", log))
                .isEqualTo("""
                        STEP2 before
                        STEP2 bootstrap=0, item=item1
                        STEP2 refresh=0, item=item1
                        STEP2 invocation=1, item=item1, result=abc123-2
                        STEP2 sink=1, item=item1
                        STEP2 refresh=1, item=item2
                        STEP2 invocation=2, item=item2, result=abc123-2
                        STEP2 sink=2, item=item2
                        STEP2 invocation=2, item=item2, result=abc123-w-2
                        STEP2 sink=2, item=item2
                        STEP2 after""");

        Assertions.assertThat(counter.get())
                .isEqualTo(2);
    }

    enum TestState implements State {

        STEP1,
        STEP2,

    }

    @RequiredArgsConstructor
    static class TestObject {

        private final String text;

        public static TestObject testObject(String text) {
            return Mockito.spy(new TestObject(text));
        }

        public String firstString() {
            return text + "-1";
        }

        public String secondMethod() {
            return text + "-2";
        }

        public String thirdMethod() {
            return text + "-3";
        }

        public String customMethod(Object value) {
            return text + "-" + value;
        }

    }

    @Data
    public static class TestStateManager implements StateManager {

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

        @Override
        public boolean isChunkProcessed(Map<String, Object> metadata, String hashString) {
            saveFlowChunkState(hashString);
            return getFlowChunkState(hashString);
        }

        @Override
        public void setChunkIsProcessed(Map<String, Object> metadata, String hashString) {
            getFlowChunkState(hashString);
        }

    }

}
