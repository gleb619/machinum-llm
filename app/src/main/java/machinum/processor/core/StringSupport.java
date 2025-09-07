package machinum.processor.core;

public interface StringSupport {

    String stringValue();

    default String shortStringValue() {
        return toString();
    }

}
