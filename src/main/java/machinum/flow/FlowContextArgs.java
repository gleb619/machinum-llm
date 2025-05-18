package machinum.flow;

import machinum.processor.core.ArgumentException;
import machinum.processor.core.ChapterWarning;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static machinum.flow.FlowContextConstants.*;

public interface FlowContextArgs {

    List<FlowArgument<?>> getArguments();

    /* ============= */

    default ChapterWarning warning() {
        return warningArg()
                .getValue();
    }

    default ChapterWarning oldWarning() {
        return oldWarningArg()
                .getValue();
    }

    default FlowArgument<ChapterWarning> warningArg() {
        return arg(WARNING_PARAM);
    }

    default FlowArgument<ChapterWarning> oldWarningArg() {
        return oldArg(WARNING_PARAM);
    }

    default  <U> FlowArgument<U> arg(String name) {
        return getArgument(name, NEW_FLAG);
    }

    default  <U> FlowArgument<U> oldArg(String name) {
        return getArgument(name, OLD_FLAG);
    }

    /* ============= */

    private <U> Optional<FlowArgument<U>> findArgument(String name, String flag) {
        return getArguments().stream()
                .filter(flowArgument -> flowArgument.getName().equals(name))
                .filter(flowArgument -> flowArgument.getType().equals(flag))
                .filter(Predicate.not(FlowArgument::isEmpty))
                .map(arg -> (FlowArgument<U>) arg)
                .findFirst();
    }

    private <U> FlowArgument<U> getArgument(String name, String flag) {
        return (FlowArgument<U>) findArgument(name, flag)
                .orElseThrow(() -> ArgumentException.forArg(FlowArgument.builder()
                        .name(name)
                        .type(flag)
                        .build()));
    }

}
