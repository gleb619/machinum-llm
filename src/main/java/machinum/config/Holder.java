package machinum.config;

import lombok.SneakyThrows;
import machinum.model.CheckedFunction;

import java.util.function.Function;
import java.util.function.Supplier;

public record Holder<T>(T data, Function<T, T> copier) implements Supplier<T> {

    public Holder(T data) {
        this(data, Function.identity());
    }

    public static <U> Holder<U> of(U data) {
        return new Holder<>(data);
    }

    @SneakyThrows
    public <U> U execute(CheckedFunction<T, U> action) {
        return action.apply(data);
    }

    public T copy() {
        return copier().apply(data());
    }

    @Override
    public T get() {
        return data;
    }

}
