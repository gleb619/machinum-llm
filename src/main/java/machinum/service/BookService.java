package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.converter.BookMapper;
import machinum.entity.BookEntity;
import machinum.exception.AppIllegalStateException;
import machinum.flow.Flow.State;
import machinum.model.Book;
import machinum.model.Book.BookState;
import machinum.model.ObjectName;
import machinum.repository.BookRepository;
import machinum.repository.BookRepository.BookExportResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static machinum.config.Config.CacheConstants.BOOKS_FOR_EXPORT;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;


    @Transactional
    //TODO split into two different methods by responsibility
    public Book save(boolean overwrite, @NonNull Book book) {
        log.debug("Prepare to save book to db: {}", book);
        var entity = bookMapper.toEntity(book);
        //TODO fix overwrite to rewrite whole book chapters
        var title = overwrite ? book.getTitle() + Math.random() : book.getTitle();
        BookEntity bookFromDb;

        if (bookRepository.existsByTitle(title)) {
            log.warn("Book already exists, return persisted book: {}", book.getTitle());
            bookFromDb = bookRepository.findByTitle(title);
        } else {
            bookFromDb = bookRepository.save(overwrite ? entity : entity.toBuilder()
                    .title(title)
                    .build());
        }

        return bookMapper.toDto(bookFromDb);
    }

    @Transactional
    public Book update(@NonNull Book book) {
        log.debug("Prepare to update book: {}", book);
        return bookMapper.toDto(bookRepository.save(bookMapper.toEntity(book)));
    }

    @Transactional(readOnly = true)
    public Book getById(@NonNull String id) {
        log.debug("Prepare to load book to db: {}", id);
        return bookRepository.findById(id)
                .map(bookMapper::toDto)
                .orElseThrow(() -> new AppIllegalStateException("Book for given id, is not found"));
    }

    @Transactional
    public void updateStartIndex(@NonNull String id, int itemIndex, int processorIndex, @NonNull State state) {
        log.debug("Prepare to increment read index in book: {}, processor={}, state={}", itemIndex, processorIndex, state);
        String localName;
        if (state instanceof Enum<?> enum0) {
            localName = enum0.name();
        } else {
            localName = String.valueOf(state);
        }

        bookRepository.updateState(id, itemIndex, processorIndex, localName);
    }

    /**
     * @param id
     * @param hashString
     * @deprecated due a bug in Postgres or Spring data layer. Calling the method results in an NPE
     */
    @Deprecated
    @Transactional
    public void addProcessedChunk(@NonNull String id, @NonNull String hashString) {
        bookRepository.addProcessedChunk(id, hashString);
    }

    @SneakyThrows
    @Transactional
    public void changeBookState(@NonNull String id, @NonNull BookState bookState) {
        bookRepository.changeBookState(id, objectMapperHolder.execute(mapper -> mapper.writeValueAsString(bookState)));
    }

    @Transactional(readOnly = true)
    public boolean hasProcessedChunk(@NonNull String id, @NonNull String hashString) {
        return bookRepository.hasProcessedChunk(id, hashString) > 0;
    }

    @Transactional(readOnly = true)
    public Page<Book> getAllBooks(PageRequest pageRequest) {
        return bookRepository.findAll(pageRequest).map(bookMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Book> findByCriteria(String query, PageRequest pageRequest) {
        return bookRepository.findByTitleContainingIgnoreCase(query, pageRequest).map(bookMapper::toDto);
    }

    @Transactional
    public void remove(String id) {
        bookRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ObjectName> exportGlossaryTranslation(String bookId) {
        return bookRepository.getGlossaryForBookId(bookId).stream()
                .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = BOOKS_FOR_EXPORT, unless = "#result == null or #result.size() == 0")
    public List<BookExportResult> getBooksForExport(int page, int size) {
        return bookRepository.findBooksForExport(PageRequest.of(page, size)).stream()
                .map(bookMapper::toExportDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookState getBookState(String id) {
        var book = getById(id);
        return book.getBookState();
    }

    @Transactional
    public void updateBookState(String id, BookState bookState) {
        var book = getById(id);
        book.setBookState(bookState);
        update(book);
    }

}
