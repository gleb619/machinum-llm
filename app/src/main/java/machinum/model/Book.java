package machinum.model;

import lombok.*;
import machinum.flow.model.Flow;
import machinum.service.BookProcessor;

import java.util.ArrayList;
import java.util.List;

import static machinum.util.TextUtil.isEmpty;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Book {

    private String id;

    private String title;

    @Builder.Default
    @ToString.Exclude
    private List<Chapter> chapters = new ArrayList<>();

    @Builder.Default
    private BookState bookState = BookState.createNew();

    private int chaptersCount;

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class BookState {

        private int itemIndex;

        private int promptIndex;

        private String state;

        @Builder.Default
        private List<String> processedChunks = new ArrayList<>();

        public static BookState createNew() {
            return builder()
                    .state(BookProcessor.ProcessorState.defaultStateName())
                    .build();
        }

        public Flow.State state() {
            if (isEmpty(state)) {
                return BookProcessor.ProcessorState.defaultState();
            }

            return BookProcessor.ProcessorState.valueOf(state);
        }

        public BookState addProcessedChunks(String hashString) {
            var list = new ArrayList<>(getProcessedChunks());
            list.add(hashString);

            return toBuilder()
                    .processedChunks(list)
                    .build();
        }

        public BookState clearState() {
            return toBuilder()
                    .itemIndex(0)
                    .promptIndex(0)
                    .state(BookProcessor.ProcessorState.defaultState().name())
                    .build();
        }

    }

}
