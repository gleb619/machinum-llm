package machinum.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.ChapterConverter;
import machinum.converter.ChapterMapper;
import machinum.listener.ChapterEntityListener;
import machinum.model.Book;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.db.DbHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static machinum.util.JavaUtil.toChunks;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookFacade {

    private final BookService bookService;
    private final ChapterConverter chapterConverter;
    private final ChapterService chapterService;
    private final ChapterMapper chapterMapper;
    private final ChapterEntityListener chapterEntityListener;
    private final DbHelper dbHelper;

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
        chapterService.importGlossaryTranslation(book, names);
    }

}
