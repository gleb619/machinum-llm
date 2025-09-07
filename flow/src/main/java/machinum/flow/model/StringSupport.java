package machinum.flow.model;

/**
 * Interface for objects that provide string representations.
 */
public interface StringSupport {

    /**
     * Returns the string value of this object.
     *
     * @return the string value
     */
    String stringValue();

    /**
     * Returns a short string value of this object, defaulting to toString().
     * @return the short string value
     */
    default String shortStringValue() {
        return toString();
    }

}
