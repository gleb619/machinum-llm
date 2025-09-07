package machinum.flow.exception;

public class AppFlowException extends IllegalStateException {

    public AppFlowException() {
    }

    public AppFlowException(String message, Object... args) {
        super(message.formatted(args));
    }

    public AppFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    public AppFlowException(Throwable cause) {
        super(cause);
    }

}
