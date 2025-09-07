package machinum.flow;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.exception.ArgumentException;
import machinum.flow.util.FlowUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static machinum.flow.FlowConstants.PREVENT_SINK;
import static machinum.flow.FlowConstants.PREVENT_STATE_UPDATE;
import static machinum.flow.FlowContextConstants.COPY_FLAG;
import static machinum.flow.FlowContextConstants.NEW_FLAG;
import static machinum.flow.util.FlowUtil.newId;

@Slf4j
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FlowContext<T> implements FlowContextArgs {

    @ToString.Include
    @Builder.Default
    private String id = newId("ctx-");

    @ToString.Include
    @Builder.Default
    private Flow.State state = Flow.State.defaultState();

    @Builder.Default
    private Flow<T> flow = Flow.from();

    @Singular("metadata")
    private Map<String, Object> metadata;

    private T currentItem;

    private int currentPipeIndex;

    @Singular
    private List<FlowArgument<?>> arguments = new ArrayList<>();

    public <U> U metadata(String name) {
        return metadata(name, null);
    }

    public <U> U metadata(String name, U defaultValue) {
        return (U) metadata.getOrDefault(name, defaultValue);
    }

    public T getPreviousItem() {
        return flow.getSource().get(iteration() - 1);
    }


    public <U> FlowArgument<U> arg(String name) {
        return getArgument(name, NEW_FLAG);
    }

    @Deprecated
    public <U> FlowArgument<U> arg(String name, Class<U> clazz) {
        return getArgument(name, NEW_FLAG)
                .map(clazz::cast);
    }


    public FlowArgument<Integer> iterationArg() {
        return FlowContextActions.iterationArg(this);
    }

    public FlowArgument<Integer> subIterationArg() {
        return FlowContextActions.subIterationArg(this);
    }

    public FlowArgument<Object> resultArg() {
        return FlowContextActions.resultArg(this);
    }

    public <U> FlowArgument<U> oldArg(String name) {
        return FlowContextActions.oldArg(this, name);
    }


    public Optional<T> findCurrentItem() {
        return Optional.ofNullable(currentItem);
    }

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    public <U> FlowContext<T> replace(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                      FlowArgument<U> newArgument) {
        var oldArgument = acquireArgument(extractor);
        var aCopy = copy(Function.identity());
        aCopy.getArguments().remove(oldArgument);
        aCopy.getArguments().add(newArgument);

        return aCopy;
    }

    public <U> FlowContext<T> rearrange(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                        FlowArgument<U> newArgument) {
        if (newArgument.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.warn("Given argument is empty: {}", newArgument);
            }
            return this;
        }

        return rearrange(extractor, b -> b.argument(newArgument));
    }

    public <U> FlowContext<T> push(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                   FlowArgument<U> newArgument) {
        return rearrange(extractor, b -> b.argument(newArgument));
    }

    public <U> FlowContext<T> rearrange(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                        Function<FlowContextBuilder<T>, FlowContextBuilder<T>> fn) {
        var currentArg = acquireArgument(extractor);
        var oldArg = currentArg.asObsolete();

        return removeArgs(oldArg, currentArg)
                .addArgs(oldArg)
                .copy(fn);
    }

    public FlowContext<T> withState(Flow.State state) {
        return copy(b -> b.state(state));
    }

    public FlowContext<T> withCurrentItem(T item) {
        return copy(b -> b.currentItem(item));
    }

    public FlowContext<T> withCurrentPipeIndex(Integer currentPipeIndex) {
        return copy(b -> b.currentPipeIndex(currentPipeIndex));
    }

    public FlowContext<T> copy(Function<FlowContextBuilder<T>, FlowContextBuilder<T>> fn) {
        var result = fn.apply(toBuilder()).build();
        result.setArguments(new HashSet<>(result.getArguments()).stream()
                .map(FlowArgument::copy)
                .sorted(Comparator.comparing(FlowArgument::getName))
                .sorted(Comparator.comparing(FlowArgument::getType))
                .sorted(FlowUtil.comparingReverse(FlowArgument::getTimestamp))
                .collect(Collectors.toList()));

        return result;
    }

    public FlowContext<T> preventChanges() {
        return preventSink()
                .preventStateUpdate();
    }

    public FlowContext<T> enableChanges() {
        return enableSink()
                .enableStateUpdate();
    }

    public FlowContext<T> preventSink() {
        return copy(b -> b.metadata(PREVENT_SINK, Boolean.TRUE));
    }

    public FlowContext<T> enableSink() {
        return removeMetadata(PREVENT_SINK);
    }

    public FlowContext<T> preventStateUpdate() {
        return copy(b -> b.metadata(PREVENT_STATE_UPDATE, Boolean.TRUE));
    }

    public FlowContext<T> enableStateUpdate() {
        return removeMetadata(PREVENT_STATE_UPDATE);
    }

    private FlowContext<T> removeMetadata(String key) {
        var localMetadata = new HashMap<>(getMetadata());
        localMetadata.remove(key);

        return copy(b -> b.clearMetadata().metadata(localMetadata));
    }

    public FlowContext<T> then(Function<FlowContext<T>, FlowContext<T>> fn) {
        return fn.apply(copy(Function.identity()))
                .copy(Function.identity());
    }

    public <U> U map(Function<FlowContext<T>, U> fn) {
        return fn.apply(copy(Function.identity()));
    }

    public <U> Optional<FlowArgument<U>> optional(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        var arg = acquireArgument(extractor);
        return arg.isEmpty() ? Optional.empty() : Optional.of(arg);
    }

    public <U> Optional<U> optionalValue(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return optional(extractor)
                .map(FlowArgument::getValue);
    }

    public FlowContext<T> peek(Consumer<FlowContext<T>> action) {
        action.accept(this);

        return this;
    }

    public FlowContext<T> removeArgs(List<FlowArgument<?>> args) {
        var localArgs = getArguments().stream()
                .filter(localArg -> {
                    for (FlowArgument<?> arg : args) {
                        boolean namesAreEqual = localArg.getName().equals(arg.getName());
                        boolean typesAreEqual = localArg.getType().equals(arg.getType());

                        if (namesAreEqual && typesAreEqual) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        return copy(b -> b.clearArguments().arguments(localArgs));
    }

    public FlowContext<T> removeArgs(FlowArgument<?>... args) {
        return removeArgs(List.of(args));
    }

    public <U> FlowContext<T> removeArgs(Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        var args = Stream.of(extractors)
                .map(this::acquireArgument)
                .toArray(FlowArgument[]::new);

        return removeArgs(args);
    }

    @Deprecated
    public <U> FlowContext<T> copyArgs(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                       Function<FlowArgument<? extends U>, FlowArgument<? extends U>> mapper) {
        var argument = acquireArgument(extractor);
        return addArgs(mapper.apply(argument.copy(b -> b.type(COPY_FLAG))));
    }

    public FlowContext<T> addArgs(FlowArgument<?>... args) {
        var localArgs = new ArrayList<>(List.of(args));
        localArgs.addAll(getArguments());

        return copy(b -> b.clearArguments().arguments(localArgs));
    }

    public FlowContext<T> withoutEphemeralArgs() {
        var newArgs = getArguments().stream()
                .filter(arg -> !arg.isEphemeral())
                .collect(Collectors.toList());

        return copy(b -> b.clearArguments().arguments(newArgs));
    }

    public FlowContext<T> withoutOldAndEmpty() {
        var toRemove = getArguments().stream()
                .filter(arg -> arg.isEmpty() || arg.isOld())
                .collect(Collectors.toList());

        return removeArgs(toRemove);
    }

    public FlowContext<T> withoutEmpty() {
        var toRemove = getArguments().stream()
                .filter(FlowArgument::isEmpty)
                .collect(Collectors.toList());

        return removeArgs(toRemove);
    }

    public <U> U parseArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                               Function<FlowArgument<? extends U>, U> action,
                               Function<Void, U> orAction) {
        var argument = acquireArgument(extractor);
        if (argument.isEmpty()) {
            return orAction.apply(null);
        } else {
            return action.apply(argument);
        }
    }

    public <U> boolean hasAnyArgument(Consumer<FlowArgument<U>> action,
                                      Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        for (var extractor : extractors) {
            var arg = acquireArgument(extractor);
            boolean result = findArgument(arg.getName(), arg.getType())
                    .isPresent();

            if (result) {
                action.accept(arg);
                return result;
            }

        }

        return false;
    }

    public <U> boolean hasArguments(Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        return Stream.of(extractors)
                .allMatch(this::hasArgument);
    }

    public <U> boolean hasArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return hasArgument(extractor, arg -> {
        });
    }

    public <U> boolean hasArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                   Consumer<FlowArgument<U>> action) {
        return hasAnyArgument(action, extractor);
    }

    public <U> Optional<FlowArgument<U>> hasArgumentOr(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                                       Consumer<FlowArgument<U>> action,
                                                       Consumer<FlowContext<? super T>> orElse) {
        boolean result = hasAnyArgument(action, extractor);

        if (!result) {
            orElse.accept(this);
        }

        return result ? Optional.of(acquireArgument(extractor)) : Optional.empty();
    }

    public <U> FlowArgument<U> resolve(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        var arg = acquireArgument(extractor);
        return getArgument(arg.getName(), arg.getType());
    }

    private <U> FlowArgument<U> acquireArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        try {
            return (FlowArgument<U>) extractor.apply(this);
        } catch (ArgumentException e) {
            return e.getFlowArgument();
        }
    }

}
