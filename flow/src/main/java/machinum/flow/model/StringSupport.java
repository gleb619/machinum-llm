package machinum.flow.model;

public interface StringSupport {

    String stringValue();

    default String shortStringValue() {
        return toString();
    }

}
