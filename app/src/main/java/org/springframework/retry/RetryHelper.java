package org.springframework.retry;

import lombok.RequiredArgsConstructor;
import machinum.exception.StopException;
import machinum.flow.FlowException;
import machinum.processor.client.GeminiClient.BusinessGeminiException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class RetryHelper {

    @Retryable(maxAttempts = 5, retryFor = Throwable.class)
    public <T> T withRetry(Supplier<T> supplier) {
        return supplier.get();
    }

    @Retryable(maxAttempts = 2, retryFor = Throwable.class)
    public <T> T withSmallRetry(Supplier<T> supplier) {
        return supplier.get();
    }

    public <T> T withRetry(String text, Function<String, T> action) {
        var ref = new AtomicReference<>(text);
        var template = createRetryTemplate(5);

        return template.execute(arg0 ->
                action.apply(
                        ref.getAndUpdate("%s\n"::formatted)));
    }

    public <T> T withSmallRetry(String text, Function<String, T> action) {
        var ref = new AtomicReference<>(text);
        var template = createRetryTemplate(2);

        return template.execute(arg0 ->
                action.apply(
                        ref.getAndUpdate("%s\n"::formatted)));
    }

    /* ============= */

    private RetryTemplate createRetryTemplate(Integer maxAttempts) {
        var retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new FixedBackOffPolicy());
        var retryPolicy = new SimpleRetryPolicy(maxAttempts, Map.of(
                Throwable.class, Boolean.TRUE,
                StopException.class, Boolean.FALSE,
                BusinessGeminiException.class, Boolean.FALSE,
                FlowException.class, Boolean.FALSE
        ));
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

}
