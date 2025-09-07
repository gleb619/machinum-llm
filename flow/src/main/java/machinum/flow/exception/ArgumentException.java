package machinum.flow.exception;

import lombok.Getter;
import machinum.flow.model.FlowArgument;

/**
 * Exception thrown when there is an issue with a flow argument.
 */
@Getter
public class ArgumentException extends IllegalArgumentException {

    private final FlowArgument flowArgument;

    /**
     * Constructs a new ArgumentException with the specified flow argument.
     *
     * @param flowArgument the flow argument associated with the exception
     */
    public ArgumentException(FlowArgument flowArgument) {
        this.flowArgument = flowArgument;
    }

    /**
     * Constructs a new ArgumentException with the specified message and flow argument.
     * @param s the detail message
     * @param flowArgument the flow argument associated with the exception
     */
    public ArgumentException(String s, FlowArgument flowArgument) {
        super(s);
        this.flowArgument = flowArgument;
    }

    /**
     * Constructs a new ArgumentException with the specified message, cause, and flow argument.
     * @param message the detail message
     * @param cause the cause
     * @param flowArgument the flow argument associated with the exception
     */
    public ArgumentException(String message, Throwable cause, FlowArgument flowArgument) {
        super(message, cause);
        this.flowArgument = flowArgument;
    }

    /**
     * Constructs a new ArgumentException with the specified cause and flow argument.
     * @param cause the cause
     * @param flowArgument the flow argument associated with the exception
     */
    public ArgumentException(Throwable cause, FlowArgument flowArgument) {
        super(cause);
        this.flowArgument = flowArgument;
    }

    /**
     * Creates a new ArgumentException for an unknown argument.
     * @param flowArgument the unknown flow argument
     * @return the created ArgumentException
     */
    public static ArgumentException forArg(FlowArgument flowArgument) {
        return new ArgumentException("Unknown argument: " + flowArgument.getName(), flowArgument);
    }

}
