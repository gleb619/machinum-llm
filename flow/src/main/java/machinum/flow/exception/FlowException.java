package machinum.flow.exception;

import lombok.Builder;
import lombok.Getter;
import machinum.flow.core.FlowContext;

import java.util.function.Function;

@Getter
public class FlowException extends RuntimeException {

    private final boolean shouldStopExecution;
    private final String reason;
    private final FlowContext<?> flowContext;

    public FlowException(String message, Throwable cause) {
        this(message, cause, true, "", null);
    }

    @Builder(builderMethodName = "superBuilder")
    public FlowException(String message, Throwable cause, boolean shouldStopExecution, String reason, FlowContext<?> flowContext) {
        super(message, cause);
        this.shouldStopExecution = shouldStopExecution;
        this.reason = reason;
        this.flowContext = flowContext;
    }

    public static FlowException superCreateNew(Function<FlowException.FlowExceptionBuilder, FlowException.FlowExceptionBuilder> creator) {
        return creator.apply(superBuilder()).build();
    }

}
