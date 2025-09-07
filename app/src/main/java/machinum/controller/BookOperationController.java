package machinum.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.service.BookProcessor;
import machinum.service.BookProcessor.ProcessorState;
import machinum.service.RawProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class BookOperationRequest {

        private String id;
        private String operationName;
        private String runner;
        @Deprecated(forRemoval = true)
        private boolean shouldPersist;
        private boolean ignoreCache;
        private boolean allowOverride;
        @Builder.Default
        private Map<String, Boolean> availableStates = new HashMap<>();
        @Builder.Default
        @JsonAlias({"ruleConfig"})
        private RuleConfig config = new RuleConfig();

        public BookOperationRequest copy(Function<BookOperationRequest.BookOperationRequestBuilder, BookOperationRequest.BookOperationRequestBuilder> builderFn) {
            return builderFn.apply(toBuilder())
                    .build();
        }

        public Map<ProcessorState, Boolean> availableStates() {
            return availableStates.entrySet().stream()
                    .collect(Collectors.toMap(e -> ProcessorState.parse(e.getKey()), Map.Entry::getValue, (f, s) -> f));
        }

        @Data
        @AllArgsConstructor
        @Builder(toBuilder = true)
        @NoArgsConstructor(access = AccessLevel.PUBLIC)
        public static class RuleConfig {

            @Builder.Default
            private RuleType ruleType = RuleType.NONE;
            @Builder.Default
            private Range range = new Range();
            @Builder.Default
            private Specific specific = new Specific();
            @Builder.Default
            private Filters filters = new Filters();

            public enum RuleType {

                ALL,
                RANGE,
                SPECIFIC,
                FILTER,
                NONE,

            }

            @Data
            @AllArgsConstructor
            @Builder(toBuilder = true)
            @NoArgsConstructor(access = AccessLevel.PUBLIC)
            public static class Range {

                private int min;
                private int max;
                private int count;

                public boolean supports(Integer currentNumber) {
                    return currentNumber > min && currentNumber <= max;
                }

            }

            @Data
            @AllArgsConstructor
            @Builder(toBuilder = true)
            @NoArgsConstructor(access = AccessLevel.PUBLIC)
            public static class Specific {

                @Builder.Default
                private List<Integer> items = new ArrayList<>();
                private int count;

            }

            @Data
            @AllArgsConstructor
            @Builder(toBuilder = true)
            @NoArgsConstructor(access = AccessLevel.PUBLIC)
            public static class Filters {

                @Builder.Default
                private List<String> selected = new ArrayList<>();
                @Builder.Default
                private List<FilterDefinition> definitions = new ArrayList<>();

            }

            @Data
            @AllArgsConstructor
            @Builder(toBuilder = true)
            @NoArgsConstructor(access = AccessLevel.PUBLIC)
            public static class FilterDefinition {

                private String id;

            }

        }

    }

}
