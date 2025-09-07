package machinum.flow;

import machinum.flow.exception.ArgumentException;
import machinum.flow.model.Chunks;
import machinum.flow.model.Chunks.ChunkItem;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static machinum.flow.FlowContextConstants.*;

public interface FlowContextArgs {

    List<FlowArgument<?>> getArguments();

    /* ============= */

    // Generic value getters
    default <U> U getValue(Function<? super FlowContextArgs, FlowArgument<U>> extractor) {
        return extractor.apply(this).getValue();
    }

    // Generic argument getters
    default <U> FlowArgument<U> arg(String name) {
        return getArgument(name, NEW_FLAG);
    }

    default <U> FlowArgument<U> oldArg(String name) {
        return getArgument(name, OLD_FLAG);
    }

    // Specific argument getters
    default FlowArgument<String> textArg() {
        return arg(TEXT_PARAM);
    }

    default FlowArgument<String> translatedTextArg() {
        return arg(TRANSLATED_TEXT_PARAM);
    }

    default FlowArgument<String> contextArg() {
        return arg(CONTEXT_PARAM);
    }

    default FlowArgument<String> consolidatedContextArg() {
        return arg(CONSOLIDATED_CONTEXT_PARAM);
    }

    default FlowArgument<String> proofreadArg() {
        return arg(PROOFREAD_PARAM);
    }

    default FlowArgument<Integer> chapterNumberArg() {
        return arg(CHAPTER_NUMBER_PARAM);
    }

    default FlowArgument<Chunks> chunksArg() {
        return arg(CHUNKS_PARAM);
    }

    default FlowArgument<ChunkItem> chunkArg() {
        return arg(CHUNK_PARAM);
    }

    default FlowArgument<Chunks> translatedChunksArg() {
        return arg(TRANSLATED_CHUNKS_PARAM);
    }

    default FlowArgument<ChunkItem> translatedChunkArg() {
        return arg(TRANSLATED_CHUNK_PARAM);
    }

    default FlowArgument<Integer> iterationArg() {
        return arg(ITERATION_PARAM);
    }

    default FlowArgument<Integer> subIterationArg() {
        return arg(SUB_ITERATION_PARAM);
    }

    default FlowArgument<Object> resultArg() {
        return arg(RESULT_PARAM);
    }

    // Old argument getters
    default FlowArgument<String> oldTextArg() {
        return oldArg(TEXT_PARAM);
    }

    default FlowArgument<String> oldTranslatedTextArg() {
        return oldArg(TRANSLATED_TEXT_PARAM);
    }

    default FlowArgument<String> oldContextArg() {
        return oldArg(CONTEXT_PARAM);
    }

    default FlowArgument<String> oldConsolidatedContextArg() {
        return oldArg(CONSOLIDATED_CONTEXT_PARAM);
    }

    default FlowArgument<String> oldProofreadArg() {
        return oldArg(PROOFREAD_PARAM);
    }

    default FlowArgument<Integer> oldChapterNumberArg() {
        return oldArg(CHAPTER_NUMBER_PARAM);
    }

    default FlowArgument<Chunks> oldChunksArg() {
        return oldArg(CHUNKS_PARAM);
    }

    default FlowArgument<ChunkItem> oldChunkArg() {
        return oldArg(CHUNK_PARAM);
    }

    default FlowArgument<Chunks> oldTranslatedChunksArg() {
        return oldArg(TRANSLATED_CHUNKS_PARAM);
    }

    default FlowArgument<ChunkItem> oldTranslatedChunkArg() {
        return oldArg(TRANSLATED_CHUNK_PARAM);
    }

    default FlowArgument<Integer> oldIterationArg() {
        return oldArg(ITERATION_PARAM);
    }

    default FlowArgument<Integer> oldSubIterationArg() {
        return oldArg(SUB_ITERATION_PARAM);
    }

    default FlowArgument<Object> oldResultArg() {
        return oldArg(RESULT_PARAM);
    }

    // Value getters
    default String text() {
        return textArg().getValue();
    }

    default String translatedText() {
        return translatedTextArg().getValue();
    }

    default String context() {
        return contextArg().getValue();
    }

    default String consolidatedContext() {
        return consolidatedContextArg().getValue();
    }

    default String proofread() {
        return proofreadArg().getValue();
    }

    default Integer chapterNumber() {
        return chapterNumberArg().getValue();
    }

    default Chunks chunks() {
        return chunksArg().getValue();
    }

    default ChunkItem chunk() {
        return chunkArg().getValue();
    }

    default Chunks translatedChunks() {
        return translatedChunksArg().getValue();
    }

    default ChunkItem translatedChunk() {
        return translatedChunkArg().getValue();
    }

    default Integer iteration() {
        return iterationArg().getValue();
    }

    default Integer subIteration() {
        return subIterationArg().getValue();
    }

    default <U> U result() {
        return (U) resultArg().getValue();
    }

    // Old value getters
    default String oldText() {
        return oldTextArg().getValue();
    }

    default String oldTranslatedText() {
        return oldTranslatedTextArg().getValue();
    }

    default String oldContext() {
        return oldContextArg().getValue();
    }

    default String oldConsolidatedContext() {
        return oldConsolidatedContextArg().getValue();
    }

    default String oldProofread() {
        return oldProofreadArg().getValue();
    }

    default Integer oldChapterNumber() {
        return oldChapterNumberArg().getValue();
    }

    default Chunks oldChunks() {
        return oldChunksArg().getValue();
    }

    default ChunkItem oldChunk() {
        return oldChunkArg().getValue();
    }

    default Chunks oldTranslatedChunks() {
        return oldTranslatedChunksArg().getValue();
    }

    default ChunkItem oldTranslatedChunk() {
        return oldTranslatedChunkArg().getValue();
    }

    default Integer oldIteration() {
        return oldIterationArg().getValue();
    }

    default Integer oldSubIteration() {
        return oldSubIterationArg().getValue();
    }

    default <U> U oldResult() {
        return (U) oldResultArg().getValue();
    }

    /* ============= */

    default <U> Optional<FlowArgument<U>> findArgument(String name, String flag) {
        return getArguments().stream()
                .filter(flowArgument -> flowArgument.getName().equals(name))
                .filter(flowArgument -> flowArgument.getType().equals(flag))
                .filter(Predicate.not(FlowArgument::isEmpty))
                .map(arg -> (FlowArgument<U>) arg)
                .findFirst();
    }

    default <U> FlowArgument<U> getArgument(String name, String flag) {
        return (FlowArgument<U>) findArgument(name, flag)
                .orElseThrow(() -> ArgumentException.forArg(FlowArgument.builder()
                        .name(name)
                        .type(flag)
                        .build()));
    }


}
