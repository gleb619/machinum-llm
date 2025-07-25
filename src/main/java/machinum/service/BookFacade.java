package machinum.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.ChapterConverter;
import machinum.converter.ChapterMapper;
import machinum.exception.AppIllegalStateException;
import machinum.listener.ChapterEntityListener;
import machinum.model.AudioFile;
import machinum.model.Book;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.db.DbHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static machinum.util.JavaUtil.toChunks;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookFacade {

    private final BookService bookService;
    private final ChapterConverter chapterConverter;
    private final ChapterService chapterService;
    private final ChapterFacade chapterFacade;
    private final ChapterMapper chapterMapper;
    private final ChapterEntityListener chapterEntityListener;
    private final DbHelper dbHelper;
    private final AudioService audioService;

    @Value("${app.batch-size}")
    private final Integer batchSize;

    @PersistenceContext
    private EntityManager entityManager;


    public Book save(boolean overwrite, @NonNull Book book) {
        log.debug("Prepare to save book with chapters to db: {}", book);
        var historyList = new ArrayList<Chapter>();
        var bookResult = dbHelper.doInNewTransaction(() -> {
            var persisted = bookService.save(overwrite, book);

            var list = book.getChapters();
            IntStream.range(0, list.size()).forEach(index -> {
                var chapter = list.get(index);

                if (Objects.isNull(chapter.getNumber())) {
                    chapter.setNumber(index + 1);
                }

                chapter.setBookId(persisted.getId());
            });

            var chunks = toChunks(list, batchSize);
            for (int i = 0; i < chunks.size(); i++) {
                var persistedChunk = chapterService.saveAll(chunks.get(i));
                historyList.addAll(persistedChunk);

                entityManager.flush();
                entityManager.clear();
            }

            return persisted;
        });

        chapterEntityListener.preUpdate(chapterMapper.toEntity(historyList));

        return bookResult;
    }

    public void importTranslation(Book book) {
        log.debug("Prepare to import translation for: {}", book);
        var chapters = book.getChapters();
        var list = new ArrayList<Chapter>();

        for (int i = 0; i < chapters.size(); i++) {
            var chapter = chapters.get(i);
            var number = i + 1;
            list.add(chapterConverter.convert(chapter, number, book.getId()));
        }

        chapterService.importTranslation(book.getId(), list);
    }

    public List<Chapter> loadBookChapters(String bookId) {
        return chapterService.loadBookChapters(bookId, PageRequest.of(0, 10_000))
                .getContent();
    }

    public List<Chapter> loadReadyChapters(String bookId, Integer from, Integer to) {
        return chapterService.loadReadyChapters(bookId, from, to);
    }

    public List<Chapter> loadReadyChapters(String bookId) {
        return chapterService.loadReadyChapters(bookId, 0, 10_000);
    }

    public Book get(String id) {
        var book = bookService.getById(id);
        var chapters = chapterService.loadBookChapters(id, PageRequest.of(0, 10_000))
                .getContent();

        return book.toBuilder()
                .chapters(chapters)
                .build();
    }

    public List<ObjectName> exportGlossaryTranslation(@NonNull String bookId) {
        log.debug("Prepare to export glossary translation for: {}", bookId);
        return bookService.exportGlossaryTranslation(bookId);
    }

    public void importGlossaryTranslation(@NonNull String bookId, @NonNull List<ObjectName> names) {
        if (names.isEmpty()) {
            return;
        }
        log.debug("Prepare to import glossary translation for: {}", bookId);
        var book = get(bookId);
        chapterFacade.importGlossaryTranslation(book, names);
    }

    public void importChaptersText(String bookId, List<Chapter> chapters) {
        Objects.requireNonNull(bookService.getById(bookId), "Book for given id, is not found");
        log.debug("Got request for chapter text changing: {}, chapters={}", bookId, chapters.size());

        dbHelper.doInNewTransaction(() -> {
            var chunks = toChunks(chapters, batchSize);
            for (int i = 0; i < chunks.size(); i++) {
                var chunk = chunks.get(i);

                for (Chapter chapWithChanges : chunk) {
                    chapterService.findByNumber(bookId, chapWithChanges.getNumber())
                            .ifPresentOrElse(chapFromDb -> {
                                chapFromDb.setText(chapWithChanges.getText());
                                chapterService.save(chapFromDb);
                            }, () -> chapterService.save(chapWithChanges.toBuilder()
                                    .id(null)
                                    .bookId(bookId)
                                    .build()));
                }

                entityManager.flush();
                entityManager.clear();
                log.debug("Processed {}/{} chunk of chapters", i + 1, chunks.size());
            }
        });
    }

    public byte[] loadBookAudio(String bookId, Integer from, Integer to, byte[] coverArt) {
        log.debug("Got request for combined mp3 file, for: bookId={}, from={}, to={}", bookId, from, to);
        var chapters = chapterService.loadReadyChapters(bookId, from, to);
        var ids = chapters.stream()
                .collect(Collectors.toMap(Chapter::getId, Chapter::getNumber, (f, s) -> f));
        var audioFiles = audioService.getByChapterIds(new ArrayList<>(ids.keySet()));

        if (ids.size() != audioFiles.size()) {
            throw new AppIllegalStateException("Some audio files was lost/not created for given request: \n%s\n%s",
                    ids.keySet(), audioFiles.stream().map(AudioFile::getChapterId).toList());
        }

        audioFiles.sort(Comparator.comparingInt(o -> ids.get(o.getChapterId())));
        byte[] bytes = audioService.joinAudioFiles(AudioService.JoinRequest.builder()
                .audioFiles(audioFiles)
                .outputName("tts_%s_%s_%s.mp3".formatted(bookId, from, to))
                .enhance(Boolean.TRUE)
                .coverArt(coverArt)
                .build());

        log.info("Combined {} files into one mp3 file: size={}", audioFiles.size(), (bytes.length / 1024));

        return bytes;
    }

}
