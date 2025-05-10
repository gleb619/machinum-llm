package machinum.controller;

import machinum.processor.core.AssistantContext;
import machinum.service.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterOperationController {

    private final ChapterProcessor chapterOperationProcessor;
    private final RawProcessor.State state;

    @SneakyThrows
    @PostMapping("/{id}/execute")
    public ResponseEntity<String> startAnalysis(@PathVariable String id, @RequestBody ChapterOperationRequest operationRequest) {
        log.debug("Got request to execute operation for chapter: {}", operationRequest);
        if (state.isFree()) {
            chapterOperationProcessor.start(operationRequest.copy(b -> b.id(id)))
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
    public static class ChapterOperationRequest {

        private String id;

        private String operationName;

        private boolean shouldPersist;

        private boolean ignoreCache;

        public ChapterOperationRequest copy(Function<ChapterOperationRequest.ChapterOperationRequestBuilder, ChapterOperationRequest.ChapterOperationRequestBuilder> builderFn) {
            return builderFn.apply(toBuilder())
                    .build();
        }

    }

}
