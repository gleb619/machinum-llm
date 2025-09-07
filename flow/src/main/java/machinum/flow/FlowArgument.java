package machinum.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.StringSupport;
import machinum.flow.util.FlowUtil;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static machinum.flow.FlowContextConstants.*;
import static machinum.flow.util.FlowUtil.newId;

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

    public FlowArgument<U> asEphemeral() {
        return copy(b -> b.ephemeral(Boolean.TRUE));
    }

    public FlowArgument<U> asAlternative() {
        var result = asEphemeral();
        result.setType(ALT_FLAG);
        return result;
    }

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

    public boolean isOld() {
        return Objects.equals(getType(), OLD_FLAG);
    }

    public <I> FlowArgument<I> map(Function<U, I> mapper) {
        var newValue = mapper.apply(value);
        FlowArgument arg = copy(Function.identity());
        arg.setValue(newValue);
        return arg;
    }

    public FlowArgument<U> mapValueWithCondition(Predicate<U> predicate, Function<U, U> mapper) {
        if (predicate.test(getValue())) {
            return copy(b -> b.value(mapper.apply(getValue())));
        } else {
            return this;
        }
    }

    public FlowArgument<U> mapValue(Function<U, U> mapper) {
        return copy(b -> b.value(mapper.apply(getValue())));
    }

    public <I> I flatMap(Function<U, I> mapper) {
        return mapper.apply(value);
    }

    public FlowArgument<U> copy(Function<FlowArgumentBuilder<U>, FlowArgumentBuilder<U>> fn) {
        return fn.apply(toBuilder()).build();
    }

    public FlowArgument<U> copy() {
        return copy(Function.identity());
    }

    @Override
    @SneakyThrows
    public String stringValue() {
        return toStringObject(StringSupport::stringValue);
    }

    @Override
    @SneakyThrows
    public String shortStringValue() {
        return toStringObject(StringSupport::shortStringValue);
    }

    public Integer countWords() {
        return FlowUtil.countWords(stringValue());
    }

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
