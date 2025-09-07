package machinum.flow.exception;

import lombok.Getter;
import machinum.flow.FlowArgument;

@Getter
public class ArgumentException extends IllegalArgumentException {

    private final FlowArgument flowArgument;

    public ArgumentException(FlowArgument flowArgument) {
        this.flowArgument = flowArgument;
    }

    public ArgumentException(String s, FlowArgument flowArgument) {
        super(s);
        this.flowArgument = flowArgument;
    }

    public ArgumentException(String message, Throwable cause, FlowArgument flowArgument) {
        super(message, cause);
        this.flowArgument = flowArgument;
    }

    public ArgumentException(Throwable cause, FlowArgument flowArgument) {
        super(cause);
        this.flowArgument = flowArgument;
    }

    public static ArgumentException forArg(FlowArgument flowArgument) {
        return new ArgumentException("Unknown argument: " + flowArgument.getName(), flowArgument);
    }

}
