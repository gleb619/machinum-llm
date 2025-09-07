package machinum.flow;

import machinum.flow.core.Flow;
import machinum.flow.core.FlowContext;
import machinum.flow.runner.OneStepRunner;
import machinum.flow.runner.RecursiveFlowRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowMainTest {

    private static List<Integer> aSomeCollectionOrFiles() {
        return List.of(1, 2, 3);
    }

    private static <T> FlowContext<T> printCurrentItem(FlowContext<T> context, Integer chapterInfo) {
        logInfo("printCurrentItem: {}", chapterInfo);
        return context;
    }

    private static <T> FlowContext<T> printHashCode(FlowContext<T> context) {
        logInfo("printHashCode: {}", context.hashCode());

        return context;
    }

    private static <T> void saveResultToDb(FlowContext<T> context) {
        logInfo("saveResultToDb: {}", context.hashCode());
        //ignore
    }

    private static void logInfo(String s, Object... args) {
        System.out.println("FlowMainTest.logInfo: " + Stream.of(args).map(Object::toString).collect(Collectors.joining(", ")));
    }

    @Test
    public void customFlowTest() {
        var currentState = TestProcessorState.PROOFREAD;
        var flow = Flow
                .from(aSomeCollectionOrFiles())
                .beforeAll(ctx -> logInfo("Started: {}", ctx.getState()))
                .aroundEach((ctx, fn) -> {
                    logInfo("Working with item: {}", ctx.getCurrentItem());
                    return fn.apply(ctx);
                })
                .afterAll(ctx -> logInfo("Ended: {}\n", ctx.getState()))
                .onState(TestProcessorState.CLEANING)
                .pipe(ctx -> {
                    var ctx2 = printHashCode(ctx);
                    return ctx2.copy(Function.identity());
                })
                .onState(TestProcessorState.PROOFREAD)
                .pipe(ctx -> {
                    var ctx2 = printCurrentItem(ctx, ctx.getCurrentItem());
                    return ctx2.copy(Function.identity());
                })
                .onState(TestProcessorState.TRANSLATE)
                .pipe(ctx -> {
                    var ctx2 = printHashCode(ctx);
                    return ctx2.copy(Function.identity());
                })
                .pipe(ctx -> {
                    var ctx2 = printCurrentItem(ctx, ctx.getCurrentItem());
                    return ctx2.copy(Function.identity());
                })
                .sink(FlowMainTest::saveResultToDb);

        var runner = new OneStepRunner(flow);
        var runner2 = new RecursiveFlowRunner<>(runner, Runnable::run);

        runner.run(currentState);
//        runner2.run(currentState);
    }

    public enum TestProcessorState implements Flow.State {

        SUMMARY,
        CLEANING,
        GLOSSARY,
        PROOFREAD,
        TRANSLATE_GLOSSARY,
        TRANSLATE_TITLE,
        TRANSLATE,
        COPYEDIT,
        SYNTHESIZE,
        FINISHED,
        ;

        public static TestProcessorState defaultState() {
            return TestProcessorState.SUMMARY;
        }

        public static String defaultStateName() {
            return TestProcessorState.SUMMARY.name();
        }

        public static TestProcessorState parse(String value) {
            try {
                return valueOf(value.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return SUMMARY;
            }
        }

    }

}
