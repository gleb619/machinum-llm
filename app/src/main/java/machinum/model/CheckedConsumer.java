package machinum.model;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.function.Consumer;

@FunctionalInterface
public interface CheckedConsumer<R> {

    static <T> Consumer<T> checked(CheckedConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                ExceptionUtils.rethrow(e);
            }
        };
    }

    void accept(R r) throws Exception;

}
