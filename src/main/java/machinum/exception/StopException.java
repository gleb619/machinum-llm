package machinum.exception;

public class StopException extends RuntimeException {

    public StopException() {
        super("StopException");
    }

    public StopException(Throwable cause) {
        super("StopException", cause);
    }

    public static StopException create() {
        return new StopException();
    }

    public static StopException create(Throwable cause) {
        return new StopException(cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // Only include the exception itself in the stack trace
        return this;
    }

}
