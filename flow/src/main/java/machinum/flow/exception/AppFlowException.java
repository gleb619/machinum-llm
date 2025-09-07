package machinum.flow.exception;

/**
 * Exception thrown to indicate an illegal state in the application flow.
 */
public class AppFlowException extends IllegalStateException {

    /**
     * Constructs a new AppFlowException with no detail message.
     */
    public AppFlowException() {
    }

    /**
     * Constructs a new AppFlowException with the specified detail message and arguments.
     *
     * @param message the detail message
     * @param args    the arguments to format the message
     */
    public AppFlowException(String message, Object... args) {
        super(message.formatted(args));
    }

    /**
     * Constructs a new AppFlowException with the specified detail message and cause.
     * @param message the detail message
     * @param cause the cause
     */
    public AppFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new AppFlowException with the specified cause.
     * @param cause the cause
     */
    public AppFlowException(Throwable cause) {
        super(cause);
    }

}
