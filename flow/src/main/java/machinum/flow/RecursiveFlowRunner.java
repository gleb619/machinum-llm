package machinum.flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class RecursiveFlowRunner<T> implements FlowRunner<T> {

    private final OneStepRunner<T> runner;
    private final Consumer<Runnable> measureWrapper;

    @Override
    public void run(Flow.State initialState) {
        log.debug("Executing whole flow from given state: {}", initialState);
        try {
            var pipes = runner.getFlow().getStatePipes();

            measureWrapper.accept(() -> filterFromKey(pipes, initialState).keySet()
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
    public FlowRunner<T> recreate(Flow<T> subFlow, Consumer<Runnable> measureWrapper) {
        return new RecursiveFlowRunner<>(
                new OneStepRunner<>(subFlow),
                measureWrapper);
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
