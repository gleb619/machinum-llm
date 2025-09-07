package machinum.flow.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.exception.ArgumentException;
import machinum.flow.model.helper.FlowContextActions;
import machinum.flow.model.helper.FlowContextArgs;
import machinum.flow.util.FlowUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static machinum.flow.constant.FlowConstants.PREVENT_SINK;
import static machinum.flow.constant.FlowConstants.PREVENT_STATE_UPDATE;
import static machinum.flow.constant.FlowContextConstants.COPY_FLAG;
import static machinum.flow.constant.FlowContextConstants.NEW_FLAG;
import static machinum.flow.util.FlowUtil.newId;

/**
 * Represents the context of a flow execution, holding state, metadata, arguments, and current processing information.
 * This class is generic over the type T, which represents the type of items being processed in the flow.
 * It provides methods to manipulate arguments, metadata, and state in an immutable way, returning new instances
 * for modifications. The context includes a unique ID, current flow state, associated flow, metadata map,
 * current item being processed, pipe index, and a list of flow arguments.
 *
 * @param <T> the type of items being processed in the flow
 */
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

    /**
     * Retrieves metadata value by name, returning null if not found.
     *
     * @param <U>  the type of the metadata value
     * @param name the name of the metadata entry
     * @return the metadata value, or null if not present
     */
    public <U> U metadata(String name) {
        return metadata(name, null);
    }

    /**
     * Retrieves metadata value by name, returning the default value if not found.
     *
     * @param <U>          the type of the metadata value
     * @param name         the name of the metadata entry
     * @param defaultValue the default value to return if the metadata is not present
     * @return the metadata value, or the default value if not present
     */
    public <U> U metadata(String name, U defaultValue) {
        return (U) metadata.getOrDefault(name, defaultValue);
    }

    /**
     * Retrieves the previous item from the flow's source based on the current iteration.
     *
     * @return the previous item in the flow
     */
    public T getPreviousItem() {
        return flow.getSource().get(iteration() - 1);
    }


    /**
     * Retrieves a flow argument by name, creating a new one if it doesn't exist.
     *
     * @param <U>  the type of the argument value
     * @param name the name of the argument
     * @return the flow argument
     */
    public <U> FlowArgument<U> arg(String name) {
        return getArgument(name, NEW_FLAG);
    }

    /**
     * Retrieves a flow argument by name and casts it to the specified class.
     * This method is deprecated; use {@link #arg(String)} instead.
     *
     * @param <U>   the type of the argument value
     * @param name  the name of the argument
     * @param clazz the class to cast the argument value to
     * @return the flow argument cast to the specified type
     * @deprecated Use {@link #arg(String)} for better type safety
     */
    @Deprecated
    public <U> FlowArgument<U> arg(String name, Class<U> clazz) {
        return getArgument(name, NEW_FLAG)
                .map(clazz::cast);
    }


    /**
     * Retrieves the iteration argument from the flow context.
     *
     * @return the iteration argument
     */
    public FlowArgument<Integer> iterationArg() {
        return FlowContextActions.iterationArg(this);
    }

    /**
     * Retrieves the sub-iteration argument from the flow context.
     *
     * @return the sub-iteration argument
     */
    public FlowArgument<Integer> subIterationArg() {
        return FlowContextActions.subIterationArg(this);
    }

    /**
     * Retrieves the result argument from the flow context.
     *
     * @return the result argument
     */
    public FlowArgument<Object> resultArg() {
        return FlowContextActions.resultArg(this);
    }

    /**
     * Retrieves an old (previous) argument by name from the flow context.
     *
     * @param <U>  the type of the argument value
     * @param name the name of the argument
     * @return the old argument
     */
    public <U> FlowArgument<U> oldArg(String name) {
        return FlowContextActions.oldArg(this, name);
    }


    /**
     * Finds the current item being processed, wrapped in an Optional.
     *
     * @return an Optional containing the current item, or empty if null
     */
    public Optional<T> findCurrentItem() {
        return Optional.ofNullable(currentItem);
    }

    /**
     * Checks if the flow context has no arguments.
     *
     * @return true if there are no arguments, false otherwise
     */
    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    /**
     * Replaces an existing argument with a new one using the provided extractor function.
     *
     * @param <U>         the type of the argument value
     * @param extractor   a function to extract the argument to replace
     * @param newArgument the new argument to replace with
     * @return a new FlowContext with the argument replaced
     */
    public <U> FlowContext<T> replace(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                      FlowArgument<U> newArgument) {
        var oldArgument = acquireArgument(extractor);
        var aCopy = copy(Function.identity());
        aCopy.getArguments().remove(oldArgument);
        aCopy.getArguments().add(newArgument);

        return aCopy;
    }

    /**
     * Rearranges an argument by replacing it with a new one, logging a warning if the new argument is empty.
     *
     * @param <U>         the type of the argument value
     * @param extractor   a function to extract the argument to rearrange
     * @param newArgument the new argument to use
     * @return a new FlowContext with the argument rearranged, or the current context if newArgument is empty
     */
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

    /**
     * Pushes a new argument by rearranging the existing one.
     *
     * @param <U>         the type of the argument value
     * @param extractor   a function to extract the argument to push
     * @param newArgument the new argument to push
     * @return a new FlowContext with the argument pushed
     */
    public <U> FlowContext<T> push(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                   FlowArgument<U> newArgument) {
        return rearrange(extractor, b -> b.argument(newArgument));
    }

    /**
     * Rearranges an argument using a builder function, marking the current argument as obsolete.
     *
     * @param <U>       the type of the argument value
     * @param extractor a function to extract the argument to rearrange
     * @param fn        a function to modify the FlowContextBuilder
     * @return a new FlowContext with the argument rearranged
     */
    public <U> FlowContext<T> rearrange(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                        Function<FlowContextBuilder<T>, FlowContextBuilder<T>> fn) {
        var currentArg = acquireArgument(extractor);
        var oldArg = currentArg.asObsolete();

        return removeArgs(oldArg, currentArg)
                .addArgs(oldArg)
                .copy(fn);
    }

    /**
     * Creates a new FlowContext with the specified state.
     *
     * @param state the new state for the flow
     * @return a new FlowContext with the updated state
     */
    public FlowContext<T> withState(Flow.State state) {
        return copy(b -> b.state(state));
    }

    /**
     * Creates a new FlowContext with the specified current item.
     *
     * @param item the new current item
     * @return a new FlowContext with the updated current item
     */
    public FlowContext<T> withCurrentItem(T item) {
        return copy(b -> b.currentItem(item));
    }

    /**
     * Creates a new FlowContext with the specified current pipe index.
     *
     * @param currentPipeIndex the new current pipe index
     * @return a new FlowContext with the updated current pipe index
     */
    public FlowContext<T> withCurrentPipeIndex(Integer currentPipeIndex) {
        return copy(b -> b.currentPipeIndex(currentPipeIndex));
    }

    /**
     * Creates a copy of the FlowContext, applying the provided function to the builder.
     * The arguments are copied, deduplicated, and sorted by name, type, and timestamp.
     *
     * @param fn a function to modify the FlowContextBuilder
     * @return a new FlowContext copy
     */
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

    /**
     * Prevents all changes by disabling sink and state updates.
     *
     * @return a new FlowContext with changes prevented
     */
    public FlowContext<T> preventChanges() {
        return preventSink()
                .preventStateUpdate();
    }

    /**
     * Enables all changes by enabling sink and state updates.
     *
     * @return a new FlowContext with changes enabled
     */
    public FlowContext<T> enableChanges() {
        return enableSink()
                .enableStateUpdate();
    }

    /**
     * Prevents sink operations by setting the prevent sink metadata flag.
     *
     * @return a new FlowContext with sink prevented
     */
    public FlowContext<T> preventSink() {
        return copy(b -> b.metadata(PREVENT_SINK, Boolean.TRUE));
    }

    /**
     * Enables sink operations by removing the prevent sink metadata flag.
     *
     * @return a new FlowContext with sink enabled
     */
    public FlowContext<T> enableSink() {
        return removeMetadata(PREVENT_SINK);
    }

    /**
     * Prevents state updates by setting the prevent state update metadata flag.
     *
     * @return a new FlowContext with state updates prevented
     */
    public FlowContext<T> preventStateUpdate() {
        return copy(b -> b.metadata(PREVENT_STATE_UPDATE, Boolean.TRUE));
    }

    /**
     * Enables state updates by removing the prevent state update metadata flag.
     *
     * @return a new FlowContext with state updates enabled
     */
    public FlowContext<T> enableStateUpdate() {
        return removeMetadata(PREVENT_STATE_UPDATE);
    }

    /**
     * Removes a metadata entry by key.
     *
     * @param key the key of the metadata to remove
     * @return a new FlowContext with the metadata removed
     */
    private FlowContext<T> removeMetadata(String key) {
        var localMetadata = new HashMap<>(getMetadata());
        localMetadata.remove(key);

        return copy(b -> b.clearMetadata().metadata(localMetadata));
    }

    /**
     * Applies a function to a copy of this FlowContext and returns another copy.
     *
     * @param fn the function to apply
     * @return a new FlowContext after applying the function
     */
    public FlowContext<T> then(Function<FlowContext<T>, FlowContext<T>> fn) {
        return fn.apply(copy(Function.identity()))
                .copy(Function.identity());
    }

    /**
     * Maps this FlowContext to a value of type U using the provided function.
     *
     * @param <U> the type of the result
     * @param fn  the mapping function
     * @return the mapped value
     */
    public <U> U map(Function<FlowContext<T>, U> fn) {
        return fn.apply(copy(Function.identity()));
    }

    /**
     * Extracts an optional argument using the provided extractor function.
     *
     * @param <U>       the type of the argument value
     * @param extractor the function to extract the argument
     * @return an Optional containing the argument if not empty, otherwise empty
     */
    public <U> Optional<FlowArgument<U>> optional(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        var arg = acquireArgument(extractor);
        return arg.isEmpty() ? Optional.empty() : Optional.of(arg);
    }

    /**
     * Extracts an optional argument value using the provided extractor function.
     *
     * @param <U>       the type of the argument value
     * @param extractor the function to extract the argument
     * @return an Optional containing the argument value if not empty, otherwise empty
     */
    public <U> Optional<U> optionalValue(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return optional(extractor)
                .map(FlowArgument::getValue);
    }

    /**
     * Performs an action on this FlowContext without modifying it.
     *
     * @param action the action to perform
     * @return this FlowContext
     */
    public FlowContext<T> peek(Consumer<FlowContext<T>> action) {
        action.accept(this);

        return this;
    }

    /**
     * Removes the specified arguments from this FlowContext.
     *
     * @param args the list of arguments to remove
     * @return a new FlowContext with the arguments removed
     */
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

    /**
     * Removes the specified arguments from this FlowContext.
     *
     * @param args the arguments to remove
     * @return a new FlowContext with the arguments removed
     */
    public FlowContext<T> removeArgs(FlowArgument<?>... args) {
        return removeArgs(List.of(args));
    }

    /**
     * Removes arguments extracted by the provided extractor functions.
     *
     * @param <U>        the type of the argument values
     * @param extractors the extractor functions for the arguments to remove
     * @return a new FlowContext with the arguments removed
     */
    public <U> FlowContext<T> removeArgs(Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        var args = Stream.of(extractors)
                .map(this::acquireArgument)
                .toArray(FlowArgument[]::new);

        return removeArgs(args);
    }

    /**
     * Copies and maps an argument, adding it to the context.
     * This method is deprecated.
     *
     * @param <U>       the type of the argument value
     * @param extractor the function to extract the argument
     * @param mapper    the function to map the argument
     * @return a new FlowContext with the copied and mapped argument added
     * @deprecated This method is deprecated and may be removed in future versions
     */
    @Deprecated
    public <U> FlowContext<T> copyArgs(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                       Function<FlowArgument<? extends U>, FlowArgument<? extends U>> mapper) {
        var argument = acquireArgument(extractor);
        return addArgs(mapper.apply(argument.copy(b -> b.type(COPY_FLAG))));
    }

    /**
     * Adds the specified arguments to this FlowContext.
     *
     * @param args the arguments to add
     * @return a new FlowContext with the arguments added
     */
    public FlowContext<T> addArgs(FlowArgument<?>... args) {
        var localArgs = new ArrayList<>(List.of(args));
        localArgs.addAll(getArguments());

        return copy(b -> b.clearArguments().arguments(localArgs));
    }

    /**
     * Creates a new FlowContext without ephemeral arguments.
     *
     * @return a new FlowContext without ephemeral arguments
     */
    public FlowContext<T> withoutEphemeralArgs() {
        var newArgs = getArguments().stream()
                .filter(arg -> !arg.isEphemeral())
                .collect(Collectors.toList());

        return copy(b -> b.clearArguments().arguments(newArgs));
    }

    /**
     * Creates a new FlowContext without old and empty arguments.
     *
     * @return a new FlowContext without old and empty arguments
     */
    public FlowContext<T> withoutOldAndEmpty() {
        var toRemove = getArguments().stream()
                .filter(arg -> arg.isEmpty() || arg.isOld())
                .collect(Collectors.toList());

        return removeArgs(toRemove);
    }

    /**
     * Creates a new FlowContext without empty arguments.
     *
     * @return a new FlowContext without empty arguments
     */
    public FlowContext<T> withoutEmpty() {
        var toRemove = getArguments().stream()
                .filter(FlowArgument::isEmpty)
                .collect(Collectors.toList());

        return removeArgs(toRemove);
    }

    /**
     * Parses an argument using the provided extractor, applying an action if present or an alternative action if empty.
     *
     * @param <U>       the type of the result
     * @param extractor the function to extract the argument
     * @param action    the action to apply if the argument is not empty
     * @param orAction  the alternative action to apply if the argument is empty
     * @return the result of applying the appropriate action
     */
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

    /**
     * Checks if any of the extracted arguments exist, applying an action to the first found argument.
     *
     * @param <U>        the type of the argument value
     * @param action     the action to apply to the found argument
     * @param extractors the extractor functions for the arguments to check
     * @return true if any argument is found, false otherwise
     */
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

    /**
     * Checks if all extracted arguments exist.
     *
     * @param <U>        the type of the argument values
     * @param extractors the extractor functions for the arguments to check
     * @return true if all arguments exist, false otherwise
     */
    public <U> boolean hasArguments(Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        return Stream.of(extractors)
                .allMatch(this::hasArgument);
    }

    /**
     * Checks if the extracted argument exists.
     *
     * @param <U>       the type of the argument value
     * @param extractor the extractor function for the argument to check
     * @return true if the argument exists, false otherwise
     */
    public <U> boolean hasArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return hasArgument(extractor, arg -> {
        });
    }

    /**
     * Checks if the extracted argument exists, applying an action if it does.
     *
     * @param <U>       the type of the argument value
     * @param extractor the extractor function for the argument to check
     * @param action    the action to apply if the argument exists
     * @return true if the argument exists, false otherwise
     */
    public <U> boolean hasArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                   Consumer<FlowArgument<U>> action) {
        return hasAnyArgument(action, extractor);
    }

    /**
     * Checks if the extracted argument exists, applying an action if it does, or an alternative action if it doesn't.
     *
     * @param <U>       the type of the argument value
     * @param extractor the extractor function for the argument to check
     * @param action    the action to apply if the argument exists
     * @param orElse    the alternative action to apply if the argument doesn't exist
     * @return an Optional containing the argument if it exists, otherwise empty
     */
    public <U> Optional<FlowArgument<U>> hasArgumentOr(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                                       Consumer<FlowArgument<U>> action,
                                                       Consumer<FlowContext<? super T>> orElse) {
        boolean result = hasAnyArgument(action, extractor);

        if (!result) {
            orElse.accept(this);
        }

        return result ? Optional.of(acquireArgument(extractor)) : Optional.empty();
    }

    /**
     * Resolves an argument using the provided extractor.
     *
     * @param <U>       the type of the argument value
     * @param extractor the extractor function for the argument
     * @return the resolved argument
     */
    public <U> FlowArgument<U> resolve(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        var arg = acquireArgument(extractor);
        return getArgument(arg.getName(), arg.getType());
    }

    /**
     * Acquires an argument using the provided extractor, handling ArgumentException.
     *
     * @param <U>       the type of the argument value
     * @param extractor the extractor function for the argument
     * @return the acquired argument
     */
    private <U> FlowArgument<U> acquireArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        try {
            return (FlowArgument<U>) extractor.apply(this);
        } catch (ArgumentException e) {
            return e.getFlowArgument();
        }
    }

}
