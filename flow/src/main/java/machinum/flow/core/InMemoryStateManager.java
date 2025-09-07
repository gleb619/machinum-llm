package machinum.flow.core;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
class InMemoryStateManager implements StateManager {

    @Builder.Default
    private int lastProcessedItem = 0;
    @Builder.Default
    private int lastProcessedPipe = 0;
    @Builder.Default
    private Flow.State state = Flow.State.defaultState();
    private boolean chunkProcessed;

    public static StateManager inMemory() {
        return inMemory(0, 0);
    }

    public static StateManager inMemory(Integer itemIndex, Integer pipeIndex) {
        return InMemoryStateManager.builder()
                .lastProcessedItem(itemIndex)
                .lastProcessedPipe(pipeIndex)
                .build();
    }

    @Override
    public void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, Flow.State state) {
        this.lastProcessedItem = itemIndex;
        this.lastProcessedPipe = pipeIndex;
    }

    @Override
    public int getLastProcessedItem(Map<String, Object> metadata) {
        return lastProcessedItem;
    }

    @Override
    public int getLastProcessorIndex(Map<String, Object> metadata) {
        return lastProcessedPipe;
    }

    @Override
    public Flow.State getState(Map<String, Object> metadata) {
        return state;
    }

    @Override
    public boolean isChunkProcessed(Map<String, Object> metadata, String hashString) {
        return chunkProcessed;
    }

    @Override
    public void setChunkIsProcessed(Map<String, Object> metadata, String hashString) {
        setChunkProcessed(Objects.nonNull(hashString));
    }

}
