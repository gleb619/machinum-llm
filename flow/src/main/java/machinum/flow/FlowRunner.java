package machinum.flow;

import java.util.function.Consumer;

public interface FlowRunner<T> {

    void run(Flow.State currentState);

    Flow<T> getFlow();

    default FlowRunner<T> recreate(Flow<T> subFlow, Consumer<Runnable> measureWrapper) {
        return this;
    }

}
