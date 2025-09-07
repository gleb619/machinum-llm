package machinum.flow.model;

public interface Mergeable<SELF extends Mergeable<SELF>> {

    SELF recreate();

    SELF merge(SELF other);

}
