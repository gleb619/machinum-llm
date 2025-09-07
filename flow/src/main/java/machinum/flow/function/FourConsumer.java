package machinum.flow.function;

@FunctionalInterface
public interface FourConsumer<F, S, T, R> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param f the first input argument
     * @param s the second input argument
     * @param t the third input argument
     * @param r the fourth input argument
     */
    void accept(F f, S s, T t, R r);

}
