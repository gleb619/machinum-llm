package machinum.processor.core;

import lombok.*;
import machinum.model.core.Mergeable;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Deprecated
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProcessorContext {

    @Builder.Default
    private String result = "";
    private Object entity;
    @Builder.Default
    private List<Message> history = new ArrayList<>();

    public static ProcessorContext create() {
        return create(Function.identity());
    }

    public static ProcessorContext create(Function<ProcessorContextBuilder, ProcessorContextBuilder> builderFn) {
        return builderFn.apply(ProcessorContext.builder()).build();
    }

    public static ProcessorContext of(String text) {
        return ProcessorContext.builder()
                .result(text)
                .build();
    }

    public String result() {
        return result;
    }

    public String result(Function<String, String> customize) {
        return customize.apply(result);
    }

    public <T> T entity() {
        return (T) entity;
    }

    public ProcessorContext copy(Function<ProcessorContextBuilder, ProcessorContextBuilder> fn) {
        return fn.apply(this.toBuilder())
                .build();
    }

    public void merge(ProcessorContext other) {
        this.result = this.result.concat(other.getResult());
        if (Objects.isNull(entity)) {
            if (other.getEntity() instanceof List otherList) {
                entity = new ArrayList<>(otherList);
            } else if (other.getEntity() instanceof Mergeable otherMergeable) {
                entity = otherMergeable.recreate();
            }
        } else if (entity instanceof List list && other.getEntity() instanceof List otherList) {
            list.addAll(otherList);
        } else if (entity instanceof List list && !(other.getEntity() instanceof List)) {
            list.add(other.getEntity());
        } else if (entity instanceof Mergeable mergeable && other.getEntity() instanceof Mergeable otherMergeable) {
            entity = mergeable.merge(otherMergeable);
        }

        this.history.clear();
        this.history.addAll(other.getHistory());
    }

}
