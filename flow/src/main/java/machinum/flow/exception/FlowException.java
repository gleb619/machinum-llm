package machinum.flow.exception;

import lombok.Builder;
import lombok.Getter;
import machinum.flow.model.FlowContext;

import java.util.function.Function;

/**
 * Exception thrown during flow execution, providing additional context and control over execution.
 */
@Getter
public class FlowException extends RuntimeException {

    private final boolean shouldStopExecution;
    private final String reason;
    private final FlowContext<?> flowContext;

    /**
     * Constructs a new FlowException with the specified message and cause, stopping execution by default.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public FlowException(String message, Throwable cause) {
        this(message, cause, true, "", null);
    }

    /**
     * Constructs a new FlowException with the specified parameters.
     * @param message the detail message
     * @param cause the cause
     * @param shouldStopExecution whether the flow execution should stop
     * @param reason the reason for the exception
     * @param flowContext the flow context
     */
    @Builder(builderMethodName = "superBuilder")
    public FlowException(String message, Throwable cause, boolean shouldStopExecution, String reason, FlowContext<?> flowContext) {
        super(message, cause);
        this.shouldStopExecution = shouldStopExecution;
        this.reason = reason;
        this.flowContext = flowContext;
    }

    /**
     * Creates a new FlowException using the provided creator function.
     * @param creator the function to configure the builder
     * @return the created FlowException
     */
    public static FlowException superCreateNew(Function<FlowException.FlowExceptionBuilder, FlowException.FlowExceptionBuilder> creator) {
        return creator.apply(superBuilder()).build();
    }

}
