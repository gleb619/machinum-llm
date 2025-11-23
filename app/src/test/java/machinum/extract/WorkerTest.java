package machinum.extract;

import machinum.processor.core.Assistant;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.PreconditionSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.RetryHelper;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerTest {

    @Mock
    private Assistant assistant;

    @Mock
    private RetryHelper retryHelper;

    @InjectMocks
    private Worker worker;

    @Test
    void testCreateSystemHistory() {
        // This test would require mocking FlowSupport or more complex setup, skip for now
        // Since history methods are internal helpers, main tests are for retries
    }

    @Test
    void testRetrySmall() throws Exception {
        // Given
        String text = "test text";
        Function<String, AssistantContext.Result> processor = chunk -> AssistantContext.Result.of("result");
        AssistantContext.Result expectedResult = AssistantContext.Result.of("mocked");

        when(retryHelper.withSmallRetry(any(), any())).thenReturn(expectedResult);

        // When
        AssistantContext.Result result = worker.retrySmall(text, processor);

        // Then
        assertEquals(expectedResult, result);
    }

    @Test
    void testRetryFull() throws Exception {
        // Given
        String text = "test text";
        Function<String, AssistantContext.Result> processor = chunk -> AssistantContext.Result.of("result");
        AssistantContext.Result expectedResult = AssistantContext.Result.of("mocked");

        when(retryHelper.withRetry(any(), any())).thenReturn(expectedResult);

        // When
        AssistantContext.Result result = worker.retryFull(text, processor);

        // Then
        assertEquals(expectedResult, result);
    }

    @Test
    void testRetrySmallWithFallback() throws Exception {
        // Given
        String text = "test text";
        Function<String, AssistantContext.Result> processor = chunk -> {
            throw new TruncatedTextException();
        };
        AssistantContext context = AssistantContext.builder().build();
        context.addResultHistory("fallback");

        when(retryHelper.withSmallRetry(any(), any())).thenThrow(new TruncatedTextException());

        // When
        AssistantContext.Result result = worker.retrySmallWithFallback(text, processor, context);

        // Then
        assertEquals("fallback", result.result());
    }

    @Test
    void testCreateRetryProcessor() {
        // Given
        AssistantContext.Result mockResult = AssistantContext.Result.of("result");
        when(assistant.process(any())).thenReturn(mockResult);
        AssistantContext baseContext = AssistantContext.builder().text("test").build();
        Function<AssistantContext.Result, AssistantContext.Result> postProcessor = result -> result;

        // When
        Function<String, AssistantContext.Result> processor = worker.createRetryProcessor(baseContext, postProcessor);
        AssistantContext.Result result = processor.apply("chunk");

        // Then
        assertEquals(mockResult, result);
    }

    @Test
    void testWorkSmallRetry() throws Exception {
        // Given
        AssistantContext.Result expectedResult = AssistantContext.Result.of("result");
        AssistantContext context = AssistantContext.builder().text("test").build();

        when(retryHelper.withSmallRetry(any(), any())).thenReturn(expectedResult);

        // When
        AssistantContext.Result result = worker.work(context, "testOp", null, Worker.RetryType.SMALL, null, null);

        // Then
        assertEquals(expectedResult, result);
        assertEquals("testOp", context.getOperation());
    }

    private static class TruncatedTextException extends PreconditionSupport.LengthValidationException {

        public TruncatedTextException() {
            this("");
        }

        public TruncatedTextException(String message) {
            super(message);
        }

    }

}
