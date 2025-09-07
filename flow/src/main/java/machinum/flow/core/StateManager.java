package machinum.flow.core;

import lombok.NonNull;
import machinum.flow.model.Flow;

import java.util.Map;

/**
 * Interface for managing state in the flow system.
 * Provides methods for persisting and loading state information.
 */
public interface StateManager extends StatePersister, ProcessedItemLoader, ProcessedPipeLoader, FlowStateLoader,
        FlowChunkLoader, FlowChunkPersister {

    /**
     * Creates a new StateManager instance using the provided components.
     *
     * @param persister          the state persister
     * @param itemLoader         the processed item loader
     * @param pipeLoader         the processed pipe loader
     * @param stateLoader        the flow state loader
     * @param flowChunkPersister the flow chunk persister
     * @param flowChunkLoader    the flow chunk loader
     * @return a new StateManager instance
     */
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

/**
 * Functional interface for loading the last processed item index.
 */
@FunctionalInterface
interface ProcessedItemLoader {

    /**
     * Gets the last processed item index for the given metadata.
     *
     * @param metadata the metadata map
     * @return the last processed item index
     */
    int getLastProcessedItem(Map<String, Object> metadata);

}

/**
 * Functional interface for loading the last processor index.
 */
@FunctionalInterface
interface ProcessedPipeLoader {

    /**
     * Gets the last processor index for the given metadata.
     *
     * @param metadata the metadata map
     * @return the last processor index
     */
    int getLastProcessorIndex(Map<String, Object> metadata);

}

/**
 * Functional interface for loading the flow state.
 */
@FunctionalInterface
interface FlowStateLoader {

    /**
     * Gets the flow state for the given metadata.
     *
     * @param metadata the metadata map
     * @return the flow state
     */
    Flow.State getState(Map<String, Object> metadata);

}

/**
 * Functional interface for checking if a chunk is processed.
 */
@FunctionalInterface
interface FlowChunkLoader {

    /**
     * Checks if the chunk with the given hash is processed for the metadata.
     *
     * @param metadata the metadata map
     * @param hashString the hash string of the chunk
     * @return true if the chunk is processed, false otherwise
     */
    boolean isChunkProcessed(Map<String, Object> metadata, String hashString);

}

/**
 * Functional interface for persisting chunk processing status.
 */
@FunctionalInterface
interface FlowChunkPersister {

    /**
     * Sets the chunk with the given hash as processed for the metadata.
     *
     * @param metadata the metadata map
     * @param hashString the hash string of the chunk
     */
    void setChunkIsProcessed(Map<String, Object> metadata, String hashString);

}

/**
 * Functional interface for persisting flow state.
 */
@FunctionalInterface
interface StatePersister {

    /**
     * Saves the flow state for the given metadata.
     *
     * @param metadata the metadata map
     * @param itemIndex the item index
     * @param pipeIndex the pipe index
     * @param state the flow state
     */
    void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, Flow.State state);

}
