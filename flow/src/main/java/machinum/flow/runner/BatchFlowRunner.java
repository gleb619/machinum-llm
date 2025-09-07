package machinum.flow.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.FlowRunner;
import machinum.flow.core.StateManager;
import machinum.flow.model.Flow;
import machinum.flow.model.Flow.State;
import machinum.flow.model.HashSupport;
import machinum.flow.util.FlowUtil;

import java.util.*;
import java.util.stream.Collectors;

import static machinum.flow.constant.FlowConstants.*;
import static machinum.flow.model.HashSupport.hashStringWithCRC32;

/**
 * Implementation of FlowRunner that processes data in configurable batches.
 * This runner divides the input data into chunks of specified size and processes
 * each chunk separately, enabling efficient processing of large datasets.
 * It uses hashing to deduplicate already processed chunks and supports resumable processing.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Configurable batch/chunk sizes for memory-efficient processing</li>
 *   <li>Automatic deduplication of processed chunks using hashing</li>
 *   <li>Resumable processing with state persistence</li>
 *   <li>Support for HashSupport-enabled items for custom hashing</li>
 *   <li>Sequential chunk processing with state transitions</li>
 * </ul>
 *
 * @param <T> the type of items being processed in batches
 */
@Slf4j
@RequiredArgsConstructor
public class BatchFlowRunner<T> implements FlowRunner<T> {

    /**
     * The underlying flow runner used to process individual chunks.
     */
    private final FlowRunner<T> flowRunner;

    /**
     * The size of each batch/chunk to process.
     */
    private final Integer chunkSize;

    /**
     * The default state to transition to after processing each chunk.
     */
    private final State defaultState;

    /**
     * Executes the flow in batches starting from the specified state.
     * Divides the input data into chunks and processes each chunk separately,
     * enabling efficient processing of large datasets with deduplication.
     *
     * @param currentState the initial state to start batch processing from
     */
    @Override
    public void run(State currentState) {
        log.debug("Executing flow in batches for given state: {}, batchSize={}", currentState, chunkSize);
        try {
            doRun(currentState);
        } finally {
            log.debug("Executed flow in batches for given state: {}", currentState);
        }
    }

    private void doRun(State currentState) {
        var processedChunks = new ArrayList<String>();
        var flow = flowRunner.getFlow().metadata(EXTEND_ENABLED, Boolean.TRUE);
        var stateManager = flow.getStateManager();
        var metadata = flow.getMetadata();
        var source = flow.getSource();
        var chunks = FlowUtil.toChunks(source, chunkSize);
        var history = new ArrayList<List<T>>();
        history.add(Collections.emptyList());

        var initState = currentState;
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            log.debug("Executing chunk({}/{})", (i + 1), chunk.size());
            try {
                var state = execute(chunk, stateManager, metadata, processedChunks, history, flow, initState);

                if (Objects.nonNull(state)) {
                    initState = state;
                }
            } finally {
                log.debug("Executed chunk({}/{})", (i + 1), chunk.size());
            }
        }
    }

    private State execute(List<T> chunk, StateManager stateManager, Map<String, Object> metadata,
                          List<String> processedChunks, List<List<T>> history, Flow<T> flow,
                          State initState) {
        var hashString = hashChunk(chunk);
        var isChunkProcessed = stateManager.isChunkProcessed(metadata, hashString);

        if (isChunkProcessed) {
            processedChunks.add(hashString);
            history.add(chunk);
            return null;
        }

        var subFlow = flow.copy(b -> {
            b.clearSource();
            b.source(chunk);
            b.metadata(PROCESSED_CHUNKS, processedChunks);
            b.metadata(PROCESSED_CHUNK, history.getLast());

            return b;
        });

        //TODO: fix bug with measure
        var runner = flowRunner.recreate(subFlow, Runnable::run);
        runner.run(initState);

        stateManager.setChunkIsProcessed(metadata, hashString);
        processedChunks.add(hashString);
        history.add(chunk);

        return defaultState;
    }

    /* ============= */

    /**
     * Generates a hash string for a chunk of data for deduplication purposes.
     * Uses custom hashing if items implement HashSupport, otherwise falls back
     * to JSON serialization for consistent hashing.
     *
     * @param list the chunk of items to hash
     * @return a CRC32 hash string representing the chunk
     * @throws RuntimeException if JSON serialization fails
     */
    @SneakyThrows
    private String hashChunk(List<T> list) {
        var hasHashSupport = list.getFirst() instanceof HashSupport;

        if (hasHashSupport) {
            return hashStringWithCRC32(list.stream()
                    .map(HashSupport.class::cast)
                    .map(HashSupport::hash)
                    .collect(Collectors.joining(";")));
        } else {
            return hashStringWithCRC32(new ObjectMapper().writeValueAsString(list));
        }
    }

    /**
     * Returns the flow configuration associated with this batch runner.
     * Delegates to the underlying flow runner to get the original flow.
     *
     * @return the flow instance containing pipes, state configuration, and metadata
     */
    @Override
    public Flow<T> getFlow() {
        return flowRunner.getFlow();
    }

}
