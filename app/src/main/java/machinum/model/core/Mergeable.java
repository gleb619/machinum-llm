package machinum.model.core;

public interface Mergeable<SELF extends Mergeable<SELF>> {

    SELF recreate();

    SELF merge(SELF other);

}
