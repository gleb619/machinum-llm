package machinum.flow.core;

import lombok.extern.slf4j.Slf4j;
import machinum.flow.exception.FlowException;

import java.util.Objects;

/**
 * Strategy for handling errors during flow execution.
 *
 * @param <T> The type of items in the flow.
 */
@FunctionalInterface
public interface ErrorStrategy<T> {

    /**
     * Returns the default error strategy (FailFast).
     *
     * @param <U> The type of items in the flow.
     * @return The default error strategy.
     */
    static <U> ErrorStrategy<U> defaultStrategy() {
        return new FailFast<>();
    }

    /**
     * Handles an error that occurred during flow execution.
     *
     * @param context   The flow context at the time of the error.
     * @param exception The exception that occurred.
     */
    void handleError(FlowContext<T> context, Exception exception);

    /**
     * Error strategy that fails fast on any error, unless the exception is a FlowException
     * with specific conditions.
     */
    @Slf4j
    class FailFast<T> implements ErrorStrategy<T> {

        @Override
        public void handleError(FlowContext<T> context, Exception exception) {
            if (exception instanceof FlowException flowException) {
                if (flowException.isShouldStopExecution()) {
                    throw new FlowException("FailFast strategy triggered", exception);
                } else if (Objects.nonNull(flowException.getReason())) {
                    log.warn("Execution will not be stopped due to reason: {}", flowException.getReason());
                    return;
                }
            }

            throw new FlowException("FailFast strategy triggered", exception);
        }
    }

    /**
     * Error strategy that ignores errors and logs them as warnings.
     */
    @Slf4j
    class IgnoreErrors<T> implements ErrorStrategy<T> {

        @Override
        public void handleError(FlowContext<T> context, Exception exception) {
            log.warn("Ignoring error: {}", exception.getMessage());
        }
    }

}
