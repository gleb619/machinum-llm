package machinum.flow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import machinum.flow.util.FlowUtil;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a collection of text chunks, each associated with a part number.
 * This class provides methods for managing, merging, and manipulating chunks of text,
 * typically used in processing large texts that are divided into smaller parts.
 * Implements {@link StringSupport} for string representation and {@link Mergeable} for merging operations.
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Chunks implements StringSupport, Mergeable<Chunks> {

    @Builder.Default
    @JsonProperty("chunks")
    private List<ChunkItem> items = new ArrayList<>();

    /**
     * Checks if the given Chunks instance is empty.
     * A Chunks is considered empty if it is null, its items list is null, or the list is empty.
     *
     * @param chunks the Chunks instance to check
     * @return true if the Chunks is empty, false otherwise
     */
    public static boolean isEmpty(Chunks chunks) {
        return Objects.isNull(chunks) || Objects.isNull(chunks.getItems()) || chunks.getItems().isEmpty();
    }

    /**
     * Creates a new empty Chunks instance.
     *
     * @return a new Chunks instance with an empty list of items
     */
    public static Chunks createNew() {
        return of(new ArrayList<>());
    }

    /**
     * Creates a Chunks instance from a list of ChunkItem objects.
     * The list is copied to prevent external modifications.
     *
     * @param chunkItems the list of ChunkItem objects to include
     * @return a new Chunks instance containing the provided items
     */
    public static Chunks of(List<ChunkItem> chunkItems) {
        return Chunks.builder()
                .items(new ArrayList<>(chunkItems))
                .build();
    }

    /**
     * Creates a Chunks instance from a single text string.
     * The text is wrapped in a ChunkItem with default part number and token count.
     *
     * @param text the text to create a chunk from
     * @return a new Chunks instance containing a single ChunkItem with the provided text
     */
    public static Chunks of(String text) {
        return of(List.of(ChunkItem.of(text)));
    }

    /**
     * Returns the string representation of all chunks combined.
     * Chunks are sorted by part number and joined with newline separators.
     *
     * @return the concatenated text of all chunks
     */
    @Override
    public String stringValue() {
        return items.stream()
                .sorted(Comparator.comparing(ChunkItem::getPart))
                .map(ChunkItem::getText)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns the number of chunks in this Chunks instance.
     *
     * @return the size of the items list
     */
    public int size() {
        return items.size();
    }

    /**
     * Returns a Stream of ChunkItem objects for functional operations.
     *
     * @return a stream of the items in this Chunks instance
     */
    public Stream<ChunkItem> stream() {
        return items.stream();
    }

    /**
     * Converts the list of ChunkItem objects to a Map keyed by part number.
     * If multiple chunks have the same part number, the first one encountered is kept.
     *
     * @return a map where keys are part numbers and values are ChunkItem objects
     */
    public Map<Integer, ChunkItem> asMap() {
        return items.stream()
                .collect(Collectors.toMap(ChunkItem::getPart, Function.identity(), (f, s) -> f));
    }

    /**
     * Adds a ChunkItem to this Chunks instance and returns this instance for method chaining.
     *
     * @param chunk the ChunkItem to add
     * @return this Chunks instance
     */
    public Chunks accumulate(ChunkItem chunk) {
        getItems().add(chunk);

        return this;
    }

    /**
     * Creates a new instance of Chunks with the same data.
     * This is used in merge operations to create a copy.
     *
     * @return a new Chunks instance with copied data
     */
    @Override
    public Chunks recreate() {
        return toBuilder()
                .build();
    }

    /**
     * Merges this Chunks instance with another Chunks instance.
     * Combines all unique ChunkItem objects from both instances, sorted by part number.
     *
     * @param other the Chunks instance to merge with
     * @return a new Chunks instance containing merged items
     */
    @Override
    public Chunks merge(Chunks other) {
        var thisChunks = new HashSet<>(getItems());
        thisChunks.addAll(other.getItems());
        var list = new ArrayList<>(thisChunks);
        list.sort(Comparator.comparing(ChunkItem::getPart));

        return of(list);
    }

    /**
     * Represents a single chunk of text with associated metadata.
     * Each chunk has a part number, the text content, and a token count.
     * Implements {@link StringSupport} for string representation.
     */
    @Data
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ChunkItem implements StringSupport {

        private int part;

        @ToString.Exclude
        private String text;

        private int tokens;

        /**
         * Creates a ChunkItem from a text string.
         * Automatically calculates the token count for the text.
         *
         * @param text the text content of the chunk
         * @return a new ChunkItem with the provided text and calculated token count
         */
        public static ChunkItem of(String text) {
            return ChunkItem.builder()
                    .text(text)
                    .tokens(FlowUtil.countTokens(text))
                    .build();
        }

        /**
         * Returns the text content of this chunk.
         *
         * @return the text of this ChunkItem
         */
        @Override
        public String stringValue() {
            return text;
        }

        /**
         * Creates a copy of this ChunkItem with modifications applied via the provided function.
         *
         * @param fn a function that takes a ChunkItemBuilder and returns a modified ChunkItemBuilder
         * @return a new ChunkItem with the modifications applied
         */
        public ChunkItem copy(Function<ChunkItem.ChunkItemBuilder, ChunkItem.ChunkItemBuilder> fn) {
            return fn.apply(toBuilder()).build();
        }

        /**
         * Creates a new ChunkItem with updated text content.
         * Automatically recalculates the token count for the new text.
         *
         * @param newText the new text content
         * @return a new ChunkItem with the updated text and recalculated token count
         */
        public ChunkItem withText(String newText) {
            return copy(b -> b.text(newText).tokens(FlowUtil.countTokens(newText)));
        }

        /**
         * Applies a mapping function to the text content of this ChunkItem.
         * Returns a new ChunkItem with the mapped text and updated token count.
         *
         * @param mapper a function to transform the text
         * @return a new ChunkItem with the transformed text
         */
        public ChunkItem map(Function<String, String> mapper) {
            return withText(mapper.apply(text));
        }

        /**
         * Tests the text content against a predicate.
         *
         * @param predicate the predicate to test the text against
         * @return true if the predicate test passes, false otherwise
         * @deprecated Use ChunkItem itself to test, to check tokens
         */
        @Deprecated
        public boolean check(Predicate<String> predicate) {
            return predicate.test(text);
        }

        /**
         * Creates a BiChunkItem pairing this ChunkItem as the origin with the provided translated ChunkItem.
         *
         * @param translated the translated version of this chunk
         * @return a BiChunkItem containing this chunk as origin and the translated chunk
         */
        public BiChunkItem toTranslated(@NonNull ChunkItem translated) {
            return BiChunkItem.of(copy(Function.identity()), translated.copy(Function.identity()));
        }

    }

    /**
     * Represents a pair of ChunkItems: an original and its translated version.
     * Used for translation workflows where both the source and target chunks need to be tracked together.
     * Implements {@link Mergeable} for merging operations.
     */
    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class BiChunkItem implements Mergeable<BiChunkItem> {

        private ChunkItem origin;

        private ChunkItem translated;

        /**
         * Creates a BiChunkItem from an origin and translated ChunkItem pair.
         *
         * @param origin     the original ChunkItem
         * @param translated the translated ChunkItem
         * @return a new BiChunkItem containing both chunks
         */
        public static BiChunkItem of(ChunkItem origin, ChunkItem translated) {
            return BiChunkItem.builder()
                    .origin(origin)
                    .translated(translated)
                    .build();
        }

        /**
         * Creates a new instance of BiChunkItem with the same data.
         * This is used in merge operations to create a copy.
         *
         * @return a new BiChunkItem instance with copied data
         */
        @Override
        public BiChunkItem recreate() {
            return toBuilder()
                    .build();
        }

        /**
         * Merges this BiChunkItem with another BiChunkItem.
         * If this instance is missing an origin or translated chunk, it will be filled from the other instance.
         *
         * @param other the BiChunkItem to merge with
         * @return a new BiChunkItem with merged data
         */
        @Override
        public BiChunkItem merge(BiChunkItem other) {
            var builder = toBuilder();

            if (Objects.isNull(origin) && Objects.nonNull(other.getOrigin())) {
                builder.origin(other.getOrigin());
            } else if (Objects.isNull(translated) && Objects.nonNull(other.getTranslated())) {
                builder.translated(other.getTranslated());
            }

            return builder.build();
        }

    }

}
