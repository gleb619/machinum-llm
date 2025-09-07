package machinum.flow.function;

@FunctionalInterface
public interface TriConsumer<T, I, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param i the second input argument
     * @param u the third input argument
     */
    void accept(T t, I i, U u);

}
