package machinum.flow.core;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.Flow;

import java.util.Map;
import java.util.Objects;

/**
 * In-memory implementation of StateManager.
 * Stores state information in memory for testing or simple use cases.
 */
@Slf4j
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class InMemoryStateManager implements StateManager {

    @Builder.Default
    private int lastProcessedItem = 0;
    @Builder.Default
    private int lastProcessedPipe = 0;
    @Builder.Default
    private Flow.State state = Flow.State.defaultState();
    private boolean chunkProcessed;

    /**
     * Creates an in-memory StateManager with default indices (0, 0).
     *
     * @return a new InMemoryStateManager instance
     */
    public static StateManager inMemory() {
        return inMemory(0, 0);
    }

    /**
     * Creates an in-memory StateManager with specified initial indices.
     *
     * @param itemIndex the initial last processed item index
     * @param pipeIndex the initial last processed pipe index
     * @return a new InMemoryStateManager instance
     */
    public static StateManager inMemory(Integer itemIndex, Integer pipeIndex) {
        return InMemoryStateManager.builder()
                .lastProcessedItem(itemIndex)
                .lastProcessedPipe(pipeIndex)
                .build();
    }

    /**
     * Saves the state by updating the last processed item and pipe indices.
     * The state parameter is ignored in this in-memory implementation.
     *
     * @param metadata  the metadata map (ignored)
     * @param itemIndex the item index to save
     * @param pipeIndex the pipe index to save
     * @param state     the flow state (ignored)
     */
    @Override
    public void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, Flow.State state) {
        this.lastProcessedItem = itemIndex;
        this.lastProcessedPipe = pipeIndex;
    }

    /**
     * Gets the last processed item index.
     *
     * @param metadata the metadata map (ignored)
     * @return the last processed item index
     */
    @Override
    public int getLastProcessedItem(Map<String, Object> metadata) {
        return lastProcessedItem;
    }

    /**
     * Gets the last processor index.
     *
     * @param metadata the metadata map (ignored)
     * @return the last processor index
     */
    @Override
    public int getLastProcessorIndex(Map<String, Object> metadata) {
        return lastProcessedPipe;
    }

    /**
     * Gets the current flow state.
     *
     * @param metadata the metadata map (ignored)
     * @return the flow state
     */
    @Override
    public Flow.State getState(Map<String, Object> metadata) {
        return state;
    }

    /**
     * Checks if the chunk is processed.
     * In this implementation, it returns the stored chunkProcessed flag, ignoring hashString.
     *
     * @param metadata the metadata map (ignored)
     * @param hashString the hash string (ignored)
     * @return true if chunk is processed, false otherwise
     */
    @Override
    public boolean isChunkProcessed(Map<String, Object> metadata, String hashString) {
        return chunkProcessed;
    }

    /**
     * Sets the chunk as processed based on whether hashString is non-null.
     *
     * @param metadata the metadata map (ignored)
     * @param hashString the hash string
     */
    @Override
    public void setChunkIsProcessed(Map<String, Object> metadata, String hashString) {
        setChunkProcessed(Objects.nonNull(hashString));
    }

}
