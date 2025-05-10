package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Chapter;
import machinum.util.JavaUtil;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.db.DbHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static machinum.config.Constants.*;

@Deprecated
@Slf4j
@Component
@RequiredArgsConstructor
public class RawProcessor {

    private final DbHelper dbHelper;

    private final TokenTextSplitter splitter;

    private final State state;


    @Deprecated
    public CompletableFuture<Void> index(String bookId, List<Chapter> chapters) {
        var localChapters = new ArrayList<>(chapters);
        state.markBusy();

        return CompletableFuture.supplyAsync(() -> {
            try {
                doIndex(bookId, localChapters);
            } catch (Exception e) {
                log.error("ERROR: ", e);
                state.free();
            }

            return null;
        }).thenApply(o -> {
            state.free();

            return (Void) o;
        });
    }

    private void doIndex(String bookId, List<Chapter> chapters) {
        log.info("Prepare to create a RAG from given chapters: {}", chapters.size());
        var counter = new AtomicInteger(1);
        var documents = new ArrayList<Document>();

//        for (var chapter : chapters) {
//            int chapterNum = counter.getAndIncrement();
//            var document = new Document(chapter.bodyString(), new HashMap<>(Map.of(
//                    TITLE_KEYWORD, chapter.header(),
//                    BOOK_ID_PARAM, bookId,
//                    NUMBER_PARAM, chapterNum,
//                    DOCUMENT_TYPE_PARAM, RAW_VALUE,
//                    CHAPTER_KEY_PARAM, chapter.getKey(),
//                    DATE_PARAM, LocalDateTime.now()
//            )));
//            documents.add(document);
//        }

        var transformer = JavaUtil.combine(
                splitter
        );

        List<Document> newDocuments = splitter.apply(documents.stream()
                .peek(d -> d.setContentFormatter(DefaultContentFormatter.builder()
                        .withExcludedInferenceMetadataKeys(CHAPTER_INFO_ID_PARAM, DOCUMENT_TYPE_PARAM, NUMBER_PARAM, CHAPTER_KEY_PARAM, BOOK_ID_PARAM)
                        .build()))
                .collect(Collectors.toList()));

//        var partitions = split(newDocuments, 20);
//
//        for (int i = 0; i < partitions.size(); i++) {
//            var partition = partitions.get(i);
//            log.debug("Sending info about partition: {}", (i + 1));
//
//            dbHelper.add(partition);
//        }

        log.info("Created RAG for given: chapters={}", chapters.size());
    }

    @Component
    public static class State {

        private final AtomicBoolean busyFlag = new AtomicBoolean();

        public void markBusy() {
            busyFlag.getAndSet(true);
        }

        public void free() {
            busyFlag.getAndSet(false);
        }

        public boolean isFree() {
            return !busyFlag.get();
        }

    }

}
