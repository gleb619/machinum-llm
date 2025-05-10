package machinum.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.service.BookProcessor;
import machinum.processor.core.HashSupport;
import machinum.util.JavaUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static machinum.config.Constants.*;
import static machinum.processor.core.HashSupport.hashStringWithCRC32;

@Slf4j
@RequiredArgsConstructor
public class BatchFlowRunner<T> implements FlowRunner<T> {

    private final FlowRunner<T> flowRunner;
    private final Integer chunkSize;

    @Override
    public void run(Flow.State currentState) {
        log.debug("Executing flow in batches for given state: {}, batchSize={}", currentState, chunkSize);
        try {
            doRun(currentState);
        } finally {
            log.debug("Executed flow in batches for given state: {}", currentState);
        }
    }

    private void doRun(Flow.State currentState) {
        var processedChunks = new ArrayList<String>();
        var flow = flowRunner.getFlow().metadata(EXTEND_ENABLED, Boolean.TRUE);
        var stateManager = flow.getStateManager();
        var metadata = flow.getMetadata();
        var source = flow.getSource();
        var chunks = JavaUtil.toChunks(source, chunkSize);
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

    private Flow.State execute(List<T> chunk, StateManager stateManager, Map<String, Object> metadata,
                               List<String> processedChunks, List<List<T>> history, Flow<T> flow,
                               Flow.State initState) {
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

        var runner = flowRunner.recreate(subFlow);
        runner.run(initState);

        stateManager.setChunkIsProcessed(metadata, hashString);
        processedChunks.add(hashString);
        history.add(chunk);

        return BookProcessor.ProcessorState.defaultState();
    }

    /* ============= */

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

    @Override
    public Flow<T> getFlow() {
        return flowRunner.getFlow();
    }

}
