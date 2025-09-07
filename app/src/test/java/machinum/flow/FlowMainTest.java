package machinum.flow;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static machinum.service.BookProcessor.ProcessorState.*;

@Slf4j
public class FlowMainTest {

    private static List<Integer> aSomeCollectionOrFiles() {
        return List.of(1, 2, 3);
    }

    private static <T> FlowContext<T> printCurrentItem(FlowContext<T> context, Integer chapterInfo) {
        log.info("printCurrentItem: {}", chapterInfo);
        return context;
    }

    private static <T> FlowContext<T> printHashCode(FlowContext<T> context) {
        log.info("printHashCode: {}", context.hashCode());

        return context;
    }

    private static <T> void saveResultToDb(FlowContext<T> context) {
        log.info("saveResultToDb: {}", context.hashCode());
        //ignore
    }

    @Test
    public void customFlowTest() {
        var currentState = PROOFREAD;
        var flow = Flow
                .from(aSomeCollectionOrFiles())
                .beforeAll(ctx -> log.info("Started: {}", ctx.getState()))
                .aroundEach((ctx, fn) -> {
                    log.info("Working with item: {}", ctx.getCurrentItem());
                    return fn.apply(ctx);
                })
                .afterAll(ctx -> log.info("Ended: {}\n", ctx.getState()))
                .onState(CLEANING)
                .pipe(ctx -> {
                    var ctx2 = printHashCode(ctx);
                    return ctx2.copy(Function.identity());
                })
                .onState(PROOFREAD)
                .pipe(ctx -> {
                    var ctx2 = printCurrentItem(ctx, ctx.getCurrentItem());
                    return ctx2.copy(Function.identity());
                })
                .onState(TRANSLATE)
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
        var runner2 = new RecursiveFlowRunner<>(runner);

        runner.run(currentState);
//        runner2.run(currentState);
    }

}
