package machinum.flow.core;

import machinum.flow.model.Flow;

import java.util.function.Consumer;

/**
 * Interface for executing flow operations with state management.
 * FlowRunner implementations handle the execution of data processing flows,
 * managing state transitions and processing items through configured pipes.
 *
 * @param <T> the type of items being processed in the flow
 */
public interface FlowRunner<T> {

    /**
     * Executes the flow starting from the specified state.
     * This method processes all items in the flow's source data according to
     * the configured pipes and state transitions.
     *
     * @param currentState the initial state to start flow execution from
     */
    void run(Flow.State currentState);

    /**
     * Returns the flow configuration associated with this runner.
     *
     * @return the flow instance containing pipes, state configuration, and metadata
     */
    Flow<T> getFlow();

    /**
     * Creates a new FlowRunner instance with the specified sub-flow and measurement wrapper.
     * This method allows for creating specialized runners for sub-flows with performance monitoring.
     *
     * @param subFlow        the sub-flow to execute
     * @param measureWrapper wrapper function for performance measurement around execution
     * @return a new FlowRunner instance configured for the sub-flow
     */
    default FlowRunner<T> recreate(Flow<T> subFlow, Consumer<Runnable> measureWrapper) {
        return this;
    }

}
