package machinum.flow.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.FlowRunner;
import machinum.flow.model.Flow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implementation of FlowRunner that executes the entire flow recursively from a given state.
 * This runner processes all states in the flow sequentially, starting from the specified initial state
 * and continuing through all subsequent states until the flow is complete.
 * It uses measurement wrapping for performance monitoring and recursive state traversal.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Recursive execution of entire flow from any starting state</li>
 *   <li>Sequential state processing with automatic state transitions</li>
 *   <li>Performance measurement and monitoring integration</li>
 *   <li>Support for complex multi-state flow execution</li>
 *   <li>Automatic flow completion detection</li>
 * </ul>
 *
 * @param <T> the type of items being processed in the flow
 */
@Slf4j
@RequiredArgsConstructor
public class RecursiveFlowRunner<T> implements FlowRunner<T> {

    /**
     * The underlying OneStepRunner used to execute individual states.
     */
    private final OneStepRunner<T> runner;

    /**
     * Wrapper function for performance measurement around execution.
     */
    private final Consumer<Runnable> measureWrapper;

    /**
     * Executes the entire flow recursively starting from the specified initial state.
     * Processes all states in the flow sequentially, applying performance measurement
     * and ensuring complete flow execution from the given starting point.
     *
     * @param initialState the state to start recursive flow execution from
     */
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

    /**
     * Returns the flow configuration associated with this recursive runner.
     * Delegates to the underlying OneStepRunner to get the flow configuration.
     *
     * @return the flow instance containing pipes, state configuration, and metadata
     */
    @Override
    public Flow<T> getFlow() {
        return runner.getFlow();
    }

    /**
     * Creates a new RecursiveFlowRunner instance with the specified sub-flow and measurement wrapper.
     * This allows for creating specialized recursive runners for sub-flows with custom performance monitoring.
     *
     * @param subFlow        the sub-flow to execute recursively
     * @param measureWrapper wrapper function for performance measurement around execution
     * @return a new RecursiveFlowRunner instance configured for the sub-flow
     */
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
