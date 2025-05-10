package machinum.flow;

public interface FlowRunner<T> {

    void run(Flow.State currentState);

    Flow<T> getFlow();

    default FlowRunner<T> recreate(Flow<T> subFlow) {
        return this;
    }

}
