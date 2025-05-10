package machinum.controller;

import machinum.model.Book;
import machinum.model.Chapter;
import machinum.service.BookProcessor;
import machinum.service.RawProcessor;
import io.micrometer.core.instrument.util.IOUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookOperationController {

    private final BookProcessor bookProcessor;
    private final RawProcessor.State state;


    @SneakyThrows
    @PostMapping("/{id}/execute")
    public ResponseEntity<String> startAnalysis(@PathVariable("id") String bookId, @RequestBody BookOperationRequest bookOperationRequest) {
        log.debug("Got request to execute operation for book: {}", bookOperationRequest);
        if (state.isFree()) {
            bookProcessor.start(bookOperationRequest.copy(b -> b.id(bookId)))
                    .get();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            Instant retryTime = Instant.now().plus(10, ChronoUnit.SECONDS);
            return ResponseEntity
                    .ok()
                    .header("X-Retry-At", retryTime.toString())
                    .body("Please retry later");
        }
    }

    @SneakyThrows
    @PostMapping("/{id}/translate-glossary")
    public ResponseEntity<String> translateGlossary(@PathVariable("id") String bookId, @RequestBody BookOperationRequest bookOperationRequest) {
        log.debug("Got request to translate glossary for book: {}", bookId);

        if (state.isFree()) {
            bookProcessor.start(bookOperationRequest.copy(b -> b.id(bookId)
                            .windowOperation(true)
                    ))
                    .get();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            Instant retryTime = Instant.now().plus(10, ChronoUnit.SECONDS);
            return ResponseEntity
                    .ok()
                    .header("X-Retry-At", retryTime.toString())
                    .body("Please retry later");
        }
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class BookOperationRequest {

        private String id;
        private String operationName;
        private String runner;
        private boolean shouldPersist;
        private boolean windowOperation;
        private boolean ignoreCache;

        public BookOperationRequest copy(Function<BookOperationRequest.BookOperationRequestBuilder, BookOperationRequest.BookOperationRequestBuilder> builderFn) {
            return builderFn.apply(toBuilder())
                    .build();
        }


    }

}
