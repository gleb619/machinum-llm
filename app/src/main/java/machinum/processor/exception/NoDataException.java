package machinum.processor.exception;

public class NoDataException extends RuntimeException {

    public NoDataException() {
        this("Ai returns empty result");
    }

    public NoDataException(String message) {
        super(message);
    }

    public NoDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public static NoDataException noData() {
        return new NoDataException();
    }

}
