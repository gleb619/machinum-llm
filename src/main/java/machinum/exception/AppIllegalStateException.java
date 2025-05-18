package machinum.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Not Found")
public class AppIllegalStateException extends IllegalStateException {

    public AppIllegalStateException() {
    }

    public AppIllegalStateException(String message, Object... args) {
        super(message.formatted(args));
    }

    public AppIllegalStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public AppIllegalStateException(Throwable cause) {
        super(cause);
    }

}
