package machinum.flow.model;

/**
 * Interface for objects that can be merged with another instance of the same type.
 *
 * @param <SELF> the type of the implementing class
 */
public interface Mergeable<SELF extends Mergeable<SELF>> {

    /**
     * Creates a new instance of this object.
     * @return a new instance
     */
    SELF recreate();

    /**
     * Merges this object with another instance.
     * @param other the other instance to merge with
     * @return the merged instance
     */
    SELF merge(SELF other);

}
