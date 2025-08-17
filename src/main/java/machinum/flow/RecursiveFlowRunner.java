package machinum.flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.util.DurationMeasureUtil;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class RecursiveFlowRunner<T> implements FlowRunner<T> {

    private final OneStepRunner<T> runner;

    @Override
    public void run(Flow.State initialState) {
        log.debug("Executing whole flow from given state: {}", initialState);
        try {
            var pipes = runner.getFlow().getStatePipes();
            DurationMeasureUtil.measure("flowRun", () -> filterFromKey(pipes, initialState).keySet()
                    .forEach(runner::run));
        } finally {
            log.debug("Executed whole flow from given state: {}", initialState);
        }
    }

    @Override
    public Flow<T> getFlow() {
        return runner.getFlow();
    }

    @Override
    public FlowRunner<T> recreate(Flow<T> subFlow) {
        return new RecursiveFlowRunner<>(
                new OneStepRunner<>(subFlow)
        );
    }

    /* ============= */

    private <K, V> Map<K, V> filterFromKey(Map<K, V> map, K key) {
        Map<K, V> result = new LinkedHashMap<>();
        boolean foundKey = false;

        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (!foundKey && entry.getKey().equals(key)) {
                foundKey = true;
            }
            if (foundKey) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

}
