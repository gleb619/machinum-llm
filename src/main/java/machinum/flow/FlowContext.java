package machinum.flow;

import machinum.model.Chunks;
import machinum.model.Chunks.ChunkItem;
import machinum.model.ObjectName;
import machinum.processor.core.ArgumentException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static machinum.config.Constants.PREVENT_SINK;
import static machinum.config.Constants.PREVENT_STATE_UPDATE;
import static machinum.flow.FlowContext.Constants.*;
import static machinum.util.JavaUtil.newId;

@Slf4j
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FlowContext<T> {

    @ToString.Include
    @Builder.Default
    private String id = newId("ctx-");

    @ToString.Include
    @Builder.Default
    private Flow.State state = Flow.State.defaultState();

    @Builder.Default
    private Flow<T> flow = Flow.from();

    @Singular("metadata")
    private Map<String, Object> metadata;

    private T currentItem;

    private int currentPipeIndex;

    @Singular
    private List<FlowArgument<?>> arguments = new ArrayList<>();

    public static FlowArgument<String> context(String value) {
        return createArg(CONTEXT_PARAM, value);
    }

    public static FlowArgument<String> consolidatedContext(String value) {
        return createArg(CONSOLIDATED_CONTEXT_PARAM, value);
    }

    public static FlowArgument<String> text(String value) {
        return createArg(TEXT_PARAM, value);
    }

    public static FlowArgument<String> translatedText(String value) {
        return createArg(TRANSLATED_TEXT_PARAM, value);
    }

    public static FlowArgument<List<ObjectName>> glossary(List<ObjectName> value) {
        return createArg(GLOSSARY_PARAM, value);
    }

    public static FlowArgument<List<ObjectName>> consolidatedGlossary(List<ObjectName> value) {
        return createArg(CONSOLIDATED_GLOSSARY_PARAM, value);
    }

    public static FlowArgument<String> proofread(String value) {
        return createArg(PROOFREAD_PARAM, value);
    }

    public static FlowArgument<Integer> chapterNumber(Integer number) {
        return createArg(CHAPTER_NUMBER_PARAM, number);
    }

    public static FlowArgument<Chunks> chunks(Chunks chunks) {
        return createArg(CHUNKS_PARAM, chunks);
    }

    public static FlowArgument<ChunkItem> chunk(ChunkItem chunk) {
        return createArg(CHUNK_PARAM, chunk);
    }

    public static FlowArgument<Chunks> translatedChunks(Chunks chunks) {
        return createArg(TRANSLATED_CHUNKS_PARAM, chunks);
    }

    public static FlowArgument<ChunkItem> translatedChunk(ChunkItem chunk) {
        return createArg(TRANSLATED_CHUNK_PARAM, chunk);
    }

    public static FlowArgument<Integer> iteration(Integer value) {
        return createArg(ITERATION_PARAM, value);
    }

    public static FlowArgument<Integer> subIteration(Integer value) {
        return createArg(SUB_ITERATION_PARAM, value);
    }

    public static FlowArgument<Object> result(Object value) {
        return createArg(RESULT_PARAM, value);
    }

    public static <U> FlowArgument<U> createArg(String name, U value) {
        return FlowArgument.<U>builder()
                .name(name)
                .value(value)
                .build();
    }

    public static <U, T> FlowContext<T> of(FlowArgument<U> arg) {
        return of(b -> b.argument(arg));
    }

    public static FlowContext<?> of(FlowArgument<?>... args) {
        return of(b -> b.arguments(List.of(args)));
    }

    public static FlowContext<?> of(Flow.State state) {
        return of(state, Map.of());
    }

    public static FlowContext<?> of(Flow.State state, Map<String, Object> metadata) {
        return of(b -> b.state(state)
                .metadata(metadata));
    }

    public static <U> FlowContext<U> of(Function<FlowContext.FlowContextBuilder<U>, FlowContext.FlowContextBuilder<U>> fn) {
        return fn.apply(FlowContext.builder())
                .build();
    }

    public <U> U metadata(String name) {
        return (U) metadata.get(name);
    }

    public String text() {
        return textArg()
                .getValue();
    }

    public String translatedText() {
        return translatedTextArg()
                .getValue();
    }

    public String context() {
        return contextArg()
                .getValue();
    }

    public String consolidatedContext() {
        return consolidatedContextArg()
                .getValue();
    }

    public List<ObjectName> glossary() {
        return glossaryArg()
                .getValue();
    }

    public List<ObjectName> consolidatedGlossary() {
        return consolidatedGlossaryArg()
                .getValue();
    }

    public String proofread() {
        return proofreadArg()
                .getValue();
    }

    public Integer chapterNumber() {
        return chapterNumberArg()
                .getValue();
    }

    public Chunks chunks() {
        return chunksArg()
                .getValue();
    }

    public ChunkItem chunk() {
        return chunkArg()
                .getValue();
    }

    public Chunks translatedChunks() {
        return translatedChunksArg()
                .getValue();
    }

    public ChunkItem translatedChunk() {
        return translatedChunkArg()
                .getValue();
    }

    public Integer iteration() {
        return iterationArg()
                .getValue();
    }

    public Integer subIteration() {
        return subIterationArg()
                .getValue();
    }

    public <U> U result() {
        return (U) resultArg()
                .getValue();
    }

    public String oldText() {
        return oldTextArg()
                .getValue();
    }

    public String oldTranslatedText() {
        return oldTranslatedTextArg()
                .getValue();
    }

    public String oldContext() {
        return oldContexArg()
                .getValue();
    }

    public String oldConsolidatedContext() {
        return oldConsolidatedContextArg()
                .getValue();
    }

    public List<ObjectName> oldGlossary() {
        return oldGlossaryArg()
                .getValue();
    }

    public String oldProofread() {
        return oldProofreadArg()
                .getValue();
    }

    public Integer oldChapterNumber() {
        return oldChapterNumberArg()
                .getValue();
    }

    public Chunks oldChunks() {
        return oldChunksArg()
                .getValue();
    }

    public ChunkItem oldChunk() {
        return oldChunkArg()
                .getValue();
    }

    public Chunks oldTranslatedChunks() {
        return oldTranslatedChunksArg()
                .getValue();
    }

    public ChunkItem oldTranslatedChunk() {
        return oldTranslatedChunkArg()
                .getValue();
    }

    public Integer oldIteration() {
        return oldIterationArg()
                .getValue();
    }

    public Integer oldSubIteration() {
        return oldSubIterationArg()
                .getValue();
    }

    public <U> U oldResult() {
        return (U) oldResultArg()
                .getValue();
    }

    public <U> FlowArgument<U> arg(String name) {
        return getArgument(name, NEW_FLAG);
    }

    @Deprecated
    public <U> FlowArgument<U> arg(String name, Class<U> clazz) {
        return getArgument(name, NEW_FLAG)
                .map(clazz::cast);
    }

    public FlowArgument<String> textArg() {
        return arg(TEXT_PARAM);
    }

    public FlowArgument<String> translatedTextArg() {
        return arg(TRANSLATED_TEXT_PARAM);
    }

    public FlowArgument<String> contextArg() {
        return arg(CONTEXT_PARAM);
    }

    public FlowArgument<String> consolidatedContextArg() {
        return arg(CONSOLIDATED_CONTEXT_PARAM);
    }

    public FlowArgument<List<ObjectName>> glossaryArg() {
        return arg(GLOSSARY_PARAM);
    }

    public FlowArgument<List<ObjectName>> consolidatedGlossaryArg() {
        return arg(CONSOLIDATED_GLOSSARY_PARAM);
    }

    public FlowArgument<String> proofreadArg() {
        return arg(PROOFREAD_PARAM);
    }

    public FlowArgument<Integer> chapterNumberArg() {
        return arg(CHAPTER_NUMBER_PARAM);
    }

    public FlowArgument<Chunks> chunksArg() {
        return arg(CHUNKS_PARAM);
    }

    public FlowArgument<ChunkItem> chunkArg() {
        return arg(CHUNK_PARAM);
    }

    public FlowArgument<Chunks> translatedChunksArg() {
        return arg(TRANSLATED_CHUNKS_PARAM);
    }

    public FlowArgument<ChunkItem> translatedChunkArg() {
        return arg(TRANSLATED_CHUNK_PARAM);
    }

    public FlowArgument<Integer> iterationArg() {
        var argument = (FlowArgument) findArgument(ITERATION_PARAM, NEW_FLAG)
                .orElseGet(() -> createArg(ITERATION_PARAM, 1));

        return argument;
    }

    public FlowArgument<Integer> subIterationArg() {
        var argument = (FlowArgument) findArgument(SUB_ITERATION_PARAM, NEW_FLAG)
                .orElseGet(() -> createArg(SUB_ITERATION_PARAM, 1));

        return argument;
    }

    public FlowArgument<Object> resultArg() {
        return arg(RESULT_PARAM);
    }

    public <U> FlowArgument<U> oldArg(String name) {
        return getArgument(name, OLD_FLAG);
    }

    public FlowArgument<String> oldTextArg() {
        return oldArg(TEXT_PARAM);
    }

    public FlowArgument<String> oldTranslatedTextArg() {
        return oldArg(TRANSLATED_TEXT_PARAM);
    }

    public FlowArgument<String> oldContexArg() {
        return oldArg(CONTEXT_PARAM);
    }

    public FlowArgument<String> oldConsolidatedContextArg() {
        return oldArg(CONSOLIDATED_CONTEXT_PARAM);
    }

    public FlowArgument<List<ObjectName>> oldGlossaryArg() {
        return oldArg(GLOSSARY_PARAM);
    }

    public FlowArgument<String> oldProofreadArg() {
        return oldArg(PROOFREAD_PARAM);
    }

    public FlowArgument<Integer> oldChapterNumberArg() {
        return oldArg(CHAPTER_NUMBER_PARAM);
    }

    public FlowArgument<Chunks> oldChunksArg() {
        return oldArg(CHUNKS_PARAM);
    }

    public FlowArgument<ChunkItem> oldChunkArg() {
        return oldArg(CHUNK_PARAM);
    }

    public FlowArgument<Chunks> oldTranslatedChunksArg() {
        return oldArg(TRANSLATED_CHUNKS_PARAM);
    }

    public FlowArgument<ChunkItem> oldTranslatedChunkArg() {
        return oldArg(TRANSLATED_CHUNK_PARAM);
    }

    public FlowArgument<Integer> oldIterationArg() {
        return oldArg(ITERATION_PARAM);
    }

    public FlowArgument<Integer> oldSubIterationArg() {
        return oldArg(SUB_ITERATION_PARAM);
    }

    public FlowArgument<Object> oldResultArg() {
        return oldArg(RESULT_PARAM);
    }

    public Optional<T> findCurrentItem() {
        return Optional.ofNullable(currentItem);
    }

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    public <U> FlowContext<T> replace(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                      FlowArgument<U> newArgument) {
        var oldArgument = acquireArgument(extractor);
        var aCopy = copy(Function.identity());
        aCopy.getArguments().remove(oldArgument);
        aCopy.getArguments().add(newArgument);

        return aCopy;
    }

    public <U> FlowContext<T> rearrange(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                        FlowArgument<U> newArgument) {
        if (newArgument.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.warn("Given argument is empty: {}", newArgument);
            }
            return this;
        }

        return rearrange(extractor, b -> b.argument(newArgument));
    }

    public <U> FlowContext<T> rearrange(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                        Function<FlowContextBuilder<T>, FlowContextBuilder<T>> fn) {
        var currentArg = acquireArgument(extractor);
        var oldArg = currentArg.obsolete();

        return removeArgs(oldArg, currentArg)
                .addArgs(oldArg)
                .copy(fn);
    }

    public FlowContext<T> withState(Flow.State state) {
        return copy(b -> b.state(state));
    }

    public FlowContext<T> withCurrentItem(T item) {
        return copy(b -> b.currentItem(item));
    }

    public FlowContext<T> withCurrentPipeIndex(Integer currentPipeIndex) {
        return copy(b -> b.currentPipeIndex(currentPipeIndex));
    }

    public FlowContext<T> copy(Function<FlowContextBuilder<T>, FlowContextBuilder<T>> fn) {
        var result = fn.apply(toBuilder()).build();
        result.setArguments(new HashSet<>(result.getArguments()).stream()
                .map(FlowArgument::copy)
                .sorted(Comparator.comparing(FlowArgument::getName))
                .sorted(Comparator.comparing(FlowArgument::getType))
                .collect(Collectors.toList()));

        return result;
    }

    public FlowContext<T> preventChanges() {
        return preventSink()
                .preventStateUpdate();
    }

    public FlowContext<T> enableChanges() {
        return enableSink()
                .enableStateUpdate();
    }

    public FlowContext<T> preventSink() {
        return copy(b -> b.metadata(PREVENT_SINK, Boolean.TRUE));
    }

    public FlowContext<T> enableSink() {
        return removeMetadata(PREVENT_SINK);
    }

    public FlowContext<T> preventStateUpdate() {
        return copy(b -> b.metadata(PREVENT_STATE_UPDATE, Boolean.TRUE));
    }

    public FlowContext<T> enableStateUpdate() {
        return removeMetadata(PREVENT_STATE_UPDATE);
    }

    private FlowContext<T> removeMetadata(String key) {
        var localMetadata = new HashMap<>(getMetadata());
        localMetadata.remove(key);

        return copy(b -> {
            b.clearMetadata();
            b.metadata(localMetadata);

            return b;
        });
    }

    public FlowContext<T> then(Function<FlowContext<T>, FlowContext<T>> fn) {
        return fn.apply(copy(Function.identity()))
                .copy(Function.identity());
    }

    public <U> U map(Function<FlowContext<T>, U> fn) {
        return fn.apply(copy(Function.identity()));
    }

    public <U> Optional<FlowArgument<U>> optional(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        var arg = acquireArgument(extractor);
        return arg.isEmpty() ? Optional.empty() : Optional.of(arg);
    }

    public <U> Optional<U> optionalValue(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return optional(extractor)
                .map(FlowArgument::getValue);
    }

    public FlowContext<T> peek(Consumer<FlowContext<T>> action) {
        action.accept(this);

        return this;
    }

    public FlowContext<T> removeArgs(FlowArgument<?>... args) {
        var localArgs = getArguments().stream()
                .filter(localArg -> {
                    for (FlowArgument<?> arg : args) {
                        boolean namesAreEqual = localArg.getName().equals(arg.getName());
                        boolean typesAreEqual = localArg.getType().equals(arg.getType());

                        if (namesAreEqual && typesAreEqual) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        return copy(b -> {
            b.clearArguments();
            b.arguments(localArgs);

            return b;
        });
    }

    public <U> FlowContext<T> removeArgs(Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        var args = Stream.of(extractors)
                .map(this::acquireArgument)
                .toArray(FlowArgument[]::new);

        return removeArgs(args);
    }

    @Deprecated
    public <U> FlowContext<T> copyArgs(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                       Function<FlowArgument<? extends U>, FlowArgument<? extends U>> mapper) {
        var argument = acquireArgument(extractor);
        return addArgs(mapper.apply(argument.copy(b -> b.type(COPY_FLAG))));
    }

    public FlowContext<T> addArgs(FlowArgument<?>... args) {
        var localArgs = new ArrayList<>(List.of(args));
        localArgs.addAll(getArguments());

        return copy(b -> {
            b.clearArguments();
            b.arguments(localArgs);

            return b;
        });
    }

    public <U> U parseArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                               Function<FlowArgument<? extends U>, U> action,
                               Function<Void, U> orAction) {
        var argument = acquireArgument(extractor);
        if (argument.isEmpty()) {
            return orAction.apply(null);
        } else {
            return action.apply(argument);
        }
    }

    public <U> boolean hasAnyArgument(Consumer<FlowArgument<U>> action,
                                      Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        for (var extractor : extractors) {
            var arg = acquireArgument(extractor);
            boolean result = findArgument(arg.getName(), arg.getType())
                    .isPresent();

            if (result) {
                action.accept(arg);
                return result;
            }

        }

        return false;
    }

    public <U> boolean hasArguments(Function<FlowContext<? super T>, FlowArgument<? extends U>>... extractors) {
        return Stream.of(extractors)
                .allMatch(this::hasArgument);
    }

    public <U> boolean hasArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        return hasArgument(extractor, arg -> {
        });
    }

    public <U> boolean hasArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                   Consumer<FlowArgument<U>> action) {
        return hasAnyArgument(action, extractor);
    }

    public <U> Optional<FlowArgument<U>> hasArgumentOr(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor,
                                                       Consumer<FlowArgument<U>> action,
                                                       Consumer<FlowContext<? super T>> orElse) {
        boolean result = hasAnyArgument(action, extractor);

        if (!result) {
            orElse.accept(this);
        }

        return result ? Optional.of(acquireArgument(extractor)) : Optional.empty();
    }

    /* ============= */

    private <U> FlowArgument<U> getArgument(String name, String flag) {
        return (FlowArgument<U>) findArgument(name, flag)
                .orElseThrow(() -> ArgumentException.forArg(FlowArgument.builder()
                        .name(name)
                        .type(flag)
                        .build()));
    }

    private <U> Optional<FlowArgument<U>> findArgument(String name, String flag) {
        return arguments.stream()
                .filter(flowArgument -> flowArgument.getName().equals(name))
                .filter(flowArgument -> flowArgument.getType().equals(flag))
                .filter(Predicate.not(FlowArgument::isEmpty))
                .map(arg -> (FlowArgument<U>) arg)
                .findFirst();
    }

    private <U> FlowArgument<U> acquireArgument(Function<FlowContext<? super T>, FlowArgument<? extends U>> extractor) {
        try {
            return (FlowArgument<U>) extractor.apply(this);
        } catch (ArgumentException e) {
            return e.getFlowArgument();
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Constants {

        public static final String NEW_FLAG = "new";

        public static final String OLD_FLAG = "old";

        public static final String COPY_FLAG = "copy";

        public static final String TEXT_PARAM = "text";

        public static final String TRANSLATED_TEXT_PARAM = "translatedText";

        public static final String CONTEXT_PARAM = "context";

        public static final String CONSOLIDATED_CONTEXT_PARAM = "consolidatedContext";

        public static final String GLOSSARY_PARAM = "glossary";

        public static final String CONSOLIDATED_GLOSSARY_PARAM = "consolidatedGlossary";

        public static final String PROOFREAD_PARAM = "proofread";

        public static final String CHAPTER_NUMBER_PARAM = "chapterNumber";

        public static final String CHUNKS_PARAM = "chunks";

        public static final String CHUNK_PARAM = "chunk";

        public static final String TRANSLATED_CHUNKS_PARAM = "translatedChunks";

        public static final String TRANSLATED_CHUNK_PARAM = "translatedChunk";

        public static final String ITERATION_PARAM = "iteration";

        public static final String SUB_ITERATION_PARAM = "subIteration";

        public static final String RESULT_PARAM = "result";

    }

}
