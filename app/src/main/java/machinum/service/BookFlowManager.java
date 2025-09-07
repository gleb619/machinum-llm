package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.StateManager;
import machinum.flow.model.Flow.State;
import machinum.repository.BookRepository;
import machinum.service.BookProcessor.ProcessorState;
import org.springframework.stereotype.Component;

import java.util.Map;

import static machinum.config.Constants.BOOK_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookFlowManager implements StateManager {

    private final BookService bookService;
    private final BookRepository bookRepository;

    @Override
    public int getLastProcessedItem(Map<String, Object> metadata) {
        var bookId = resolveBookId(metadata);
        return bookRepository.getItemIndexById(bookId);
    }

    @Override
    public int getLastProcessorIndex(Map<String, Object> metadata) {
        var bookId = resolveBookId(metadata);
        return bookRepository.getProcessorIndexById(bookId);
    }

    @Override
    public State getState(Map<String, Object> metadata) {
        var bookId = resolveBookId(metadata);
        return ProcessorState.valueOf(bookRepository.getStateById(bookId));
    }

    @Override
    public void saveState(Map<String, Object> metadata, int itemIndex, int pipeIndex, State state) {
        var bookId = resolveBookId(metadata);
        bookService.updateStartIndex(bookId, itemIndex, pipeIndex, state);
    }

    @Override
    public boolean isChunkProcessed(Map<String, Object> metadata, String hashString) {
        var bookId = resolveBookId(metadata);
        return bookService.hasProcessedChunk(bookId, hashString);
    }

    @Override
    public void setChunkIsProcessed(Map<String, Object> metadata, String hashString) {
        var bookId = resolveBookId(metadata);
        var book = bookService.getById(bookId);
        bookService.changeBookState(bookId, book.getBookState()
                .addProcessedChunks(hashString)
                .clearState());
    }

    /* ============= */

    private String resolveBookId(Map<String, Object> metadata) {
        return metadata.get(BOOK_ID).toString();
    }

}
