package machinum.model;

@FunctionalInterface
public interface CheckedSupplier<R> {

    R get() throws Exception;

}
