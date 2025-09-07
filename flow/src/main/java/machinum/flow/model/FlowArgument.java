package machinum.flow.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.util.FlowUtil;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static machinum.flow.constant.FlowContextConstants.*;
import static machinum.flow.util.FlowUtil.newId;

/**
 * A generic class representing an argument in a flow processing system.
 * It holds a value of type U along with metadata such as id, name, type, timestamp, and ephemeral flag.
 * Provides methods for transforming, copying, and converting the argument.
 *
 * @param <U> the type of the value contained in this argument
 */
@Slf4j
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FlowArgument<U> implements StringSupport {

    @Builder.Default
    private String id = newId("arg-");

    private String name;

    @Builder.Default
    private String type = NEW_FLAG;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private U value;

    @Builder.Default
    @ToString.Exclude
    private Instant timestamp = Instant.now();

    @Builder.Default
    @ToString.Exclude
    private boolean ephemeral = Boolean.FALSE;

    /**
     * Creates a new FlowArgument instance marked as ephemeral.
     *
     * @return a new FlowArgument with ephemeral set to true
     */
    public FlowArgument<U> asEphemeral() {
        return copy(b -> b.ephemeral(Boolean.TRUE));
    }

    /**
     * Creates a new FlowArgument instance marked as alternative and ephemeral.
     *
     * @return a new FlowArgument with type set to ALT_FLAG and ephemeral set to true
     */
    public FlowArgument<U> asAlternative() {
        var result = asEphemeral();
        result.setType(ALT_FLAG);
        return result;
    }

    /**
     * Marks this argument as obsolete based on its current state.
     * If type is NEW_FLAG, changes type to OLD_FLAG.
     * If empty, returns this instance.
     * Otherwise, sets value to null.
     *
     * @return a new FlowArgument marked as obsolete
     */
    public FlowArgument<U> asObsolete() {
        if (NEW_FLAG.equals(getType())) {
            return toBuilder()
                    .type(OLD_FLAG)
                    .build();
        } else if (isEmpty()) {
            return this;
        } else {
            return toBuilder()
                    .value(null)
                    .build();
        }
    }

    /**
     * Checks if the value is empty.
     * For null values, returns true.
     * For List, checks if empty.
     * For String, checks if blank.
     *
     * @return true if the value is considered empty, false otherwise
     */
    public boolean isEmpty() {
        boolean isNull = Objects.isNull(value);

        if (!isNull) {
            if (value instanceof List<?> l) {
                return l.isEmpty();
            } else if (value instanceof String s) {
                return s.isBlank();
            }
        }

        return isNull;
    }

    /**
     * Checks if this argument is marked as old.
     *
     * @return true if type equals OLD_FLAG, false otherwise
     */
    public boolean isOld() {
        return Objects.equals(getType(), OLD_FLAG);
    }

    /**
     * Applies a mapping function to the value and returns a new FlowArgument with the mapped value.
     *
     * @param <I>    the type of the mapped value
     * @param mapper the function to apply to the value
     * @return a new FlowArgument with the mapped value
     */
    public <I> FlowArgument<I> map(Function<U, I> mapper) {
        var newValue = mapper.apply(value);
        FlowArgument arg = copy(Function.identity());
        arg.setValue(newValue);
        return arg;
    }

    /**
     * Applies a mapping function to the value if the predicate is true.
     *
     * @param predicate the condition to test the value
     * @param mapper the function to apply if predicate is true
     * @return a new FlowArgument with the mapped value if condition met, otherwise this instance
     */
    public FlowArgument<U> mapValueWithCondition(Predicate<U> predicate, Function<U, U> mapper) {
        if (predicate.test(getValue())) {
            return copy(b -> b.value(mapper.apply(getValue())));
        } else {
            return this;
        }
    }

    /**
     * Applies a mapping function to the value.
     *
     * @param mapper the function to apply to the value
     * @return a new FlowArgument with the mapped value
     */
    public FlowArgument<U> mapValue(Function<U, U> mapper) {
        return copy(b -> b.value(mapper.apply(getValue())));
    }

    /**
     * Applies a mapping function to the value and returns the result directly.
     *
     * @param <I> the type of the result
     * @param mapper the function to apply to the value
     * @return the result of applying the mapper to the value
     */
    public <I> I flatMap(Function<U, I> mapper) {
        return mapper.apply(value);
    }

    /**
     * Creates a copy of this FlowArgument with modifications applied via the builder function.
     *
     * @param fn the function to modify the builder
     * @return a new FlowArgument with the modifications
     */
    public FlowArgument<U> copy(Function<FlowArgumentBuilder<U>, FlowArgumentBuilder<U>> fn) {
        return fn.apply(toBuilder()).build();
    }

    /**
     * Creates a copy of this FlowArgument.
     *
     * @return a new FlowArgument identical to this one
     */
    public FlowArgument<U> copy() {
        return copy(Function.identity());
    }

    /**
     * Converts the value to its string representation.
     *
     * @return the string value of the argument
     */
    @Override
    @SneakyThrows
    public String stringValue() {
        return toStringObject(StringSupport::stringValue);
    }

    /**
     * Converts the value to its short string representation.
     *
     * @return the short string value of the argument
     */
    @Override
    @SneakyThrows
    public String shortStringValue() {
        return toStringObject(StringSupport::shortStringValue);
    }

    /**
     * Counts the number of words in the string value.
     *
     * @return the word count
     */
    public Integer countWords() {
        return FlowUtil.countWords(stringValue());
    }

    /**
     * Counts the number of tokens in the string value.
     *
     * @return the token count
     */
    public Integer countTokens() {
        return FlowUtil.countTokens(stringValue());
    }

    /* ============= */

    private String toStringObject(Function<StringSupport, String> mapper) {
        try {
            if (value instanceof String s) {
                return s;
            } else if (value instanceof StringSupport str) {
                return mapper.apply(str);
            } else if (value instanceof List<?> list) {
                if (!list.isEmpty() && list.getFirst() instanceof StringSupport) {
                    return ((List<StringSupport>) list).stream()
                            .map(mapper::apply)
                            .collect(Collectors.joining("\n"));
                }
            }

            return new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);
        } catch (Exception e) {
            log.error("Found corrupted value: {}", value);
            return FlowUtil.rethrow(e);
        }
    }

}
