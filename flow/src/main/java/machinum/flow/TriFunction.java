package machinum.flow;

@FunctionalInterface
public interface TriFunction<T, I, U, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param i the second function argument
     * @param u the third function argument
     * @return the function result
     */
    R apply(T t, I i, U u);

}
