package machinum.model;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.function.Function;

@FunctionalInterface
public interface CheckedFunction<T, R> {

    static <I, O> Function<I, O> checked(CheckedFunction<I, O> function) {
        return i -> {
            try {
                return function.apply(i);
            } catch (Exception e) {
                return ExceptionUtils.rethrow(e);
            }
        };
    }

    R apply(T t) throws Exception;

}
