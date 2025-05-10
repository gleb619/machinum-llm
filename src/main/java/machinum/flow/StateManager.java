package machinum.flow;

import lombok.NonNull;

import java.util.Map;

public interface StateManager extends StatePersister, ProcessedItemLoader, ProcessedPipeLoader, FlowStateLoader,
        FlowChunkLoader, FlowChunkPersister {

    static StateManager create(@NonNull StatePersister persister,
                               @NonNull ProcessedItemLoader itemLoader,
                               @NonNull ProcessedPipeLoader pipeLoader,
                               @NonNull FlowStateLoader stateLoader,
                               @NonNull FlowChunkPersister flowChunkPersister,
                               @NonNull FlowChunkLoader flowChunkLoader) {
        return new StateManager() {

            @Override
            public void setChunkIsProcessed(Map<String, Object> metadata, String hashString) {
                flowChunkPersister.setChunkIsProcessed(metadata, hashString);
            }

            @Override
            public int getLastProcessedItem(Map<String, Object> metadata) {
                return itemLoader.getLastProcessedItem(metadata);
            }

            @Override
            public int getLastProcessorIndex(Map<String, Object> metadata) {
                return pipeLoader.getLastProcessorIndex(metadata);
            }

            @Override
            public Flow.State getState(Map<String, Object> metadata) {
                return stateLoader.getState(metadata);
            }

            @Override
            public boolean isChunkProcessed(Map<String, Object> metadata, String hashString) {
                return flowChunkLoader.isChunkProcessed(metadata, hashString);
            }

            @Override
            public void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, Flow.State state) {
                persister.saveState(metadata, itemIndex, pipeIndex, state);
            }

        };
    }

}

@FunctionalInterface
interface ProcessedItemLoader {

    int getLastProcessedItem(Map<String, Object> metadata);

}

@FunctionalInterface
interface ProcessedPipeLoader {

    int getLastProcessorIndex(Map<String, Object> metadata);

}

@FunctionalInterface
interface FlowStateLoader {

    Flow.State getState(Map<String, Object> metadata);

}

@FunctionalInterface
interface FlowChunkLoader {

    boolean isChunkProcessed(Map<String, Object> metadata, String hashString);

}

@FunctionalInterface
interface FlowChunkPersister {

    void setChunkIsProcessed(Map<String, Object> metadata, String hashString);

}

@FunctionalInterface
interface StatePersister {

    void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, Flow.State state);

}