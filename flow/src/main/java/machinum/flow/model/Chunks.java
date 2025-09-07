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

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Chunks implements StringSupport, Mergeable<Chunks> {

    @Builder.Default
    @JsonProperty("chunks")
    private List<ChunkItem> items = new ArrayList<>();

    public static boolean isEmpty(Chunks chunks) {
        return Objects.isNull(chunks) || Objects.isNull(chunks.getItems()) || chunks.getItems().isEmpty();
    }

    public static Chunks createNew() {
        return of(new ArrayList<>());
    }

    public static Chunks of(List<ChunkItem> chunkItems) {
        return Chunks.builder()
                .items(new ArrayList<>(chunkItems))
                .build();
    }

    public static Chunks of(String text) {
        return of(List.of(ChunkItem.of(text)));
    }

    @Override
    public String stringValue() {
        return items.stream()
                .sorted(Comparator.comparing(ChunkItem::getPart))
                .map(ChunkItem::getText)
                .collect(Collectors.joining("\n"));
    }

    public int size() {
        return items.size();
    }

    public Stream<ChunkItem> stream() {
        return items.stream();
    }

    public Map<Integer, ChunkItem> asMap() {
        return items.stream()
                .collect(Collectors.toMap(ChunkItem::getPart, Function.identity(), (f, s) -> f));
    }

    public Chunks accumulate(ChunkItem chunk) {
        getItems().add(chunk);

        return this;
    }

    @Override
    public Chunks recreate() {
        return toBuilder()
                .build();
    }

    @Override
    public Chunks merge(Chunks other) {
        var thisChunks = new HashSet<>(getItems());
        thisChunks.addAll(other.getItems());
        var list = new ArrayList<>(thisChunks);
        list.sort(Comparator.comparing(ChunkItem::getPart));

        return of(list);
    }

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

        public static ChunkItem of(String text) {
            return ChunkItem.builder()
                    .text(text)
                    .tokens(FlowUtil.countTokens(text))
                    .build();
        }

        @Override
        public String stringValue() {
            return text;
        }

        public ChunkItem copy(Function<ChunkItem.ChunkItemBuilder, ChunkItem.ChunkItemBuilder> fn) {
            return fn.apply(toBuilder()).build();
        }

        public ChunkItem withText(String newText) {
            return copy(b -> b.text(newText).tokens(FlowUtil.countTokens(newText)));
        }

        public ChunkItem map(Function<String, String> mapper) {
            return withText(mapper.apply(text));
        }

        /**
         * @param predicate
         * @return
         * @deprecated add ChunkItem itself to test, to check tokens
         */
        @Deprecated
        public boolean check(Predicate<String> predicate) {
            return predicate.test(text);
        }

        public BiChunkItem toTranslated(@NonNull ChunkItem translated) {
            return BiChunkItem.of(copy(Function.identity()), translated.copy(Function.identity()));
        }

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class BiChunkItem implements Mergeable<BiChunkItem> {

        private ChunkItem origin;

        private ChunkItem translated;

        public static BiChunkItem of(ChunkItem origin, ChunkItem translated) {
            return BiChunkItem.builder()
                    .origin(origin)
                    .translated(translated)
                    .build();
        }

        @Override
        public BiChunkItem recreate() {
            return toBuilder()
                    .build();
        }

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
