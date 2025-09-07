package machinum.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.exception.AppIllegalStateException;
import machinum.util.JavaUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MachinumAutomataRestClient {

    public static final int MAX_RETRIES = 3;
    public static final int DEFAULT_BACKOFF_TIME = 1000;
    @Value("${app.ma-service.url}")
    private final String maServiceUrl;

    @Value("${app.ma-service.script}")
    private final String maServiceScript;

    @Qualifier("maHttpClient")
    private final Holder<HttpClient> httpClient;

    @Qualifier("maObjectMapper")
    private final Holder<ObjectMapper> objectMapper;

    public TranslateResponse translateScript(String sourceText) {
        log.debug("Sending text to translate via external service");
        URI uri = URI.create("%s/api/scripts/%s/execute".formatted(maServiceUrl, maServiceScript));
        HttpRequest request = createHttpRequest(sourceText, uri);

        var response = executeWithRetry(request, uri);
        var maResponse = parseResponse(response);

        if (!maResponse.isSuccess()) {
            throw new AppIllegalStateException("Translation failed");
        }

        log.debug("Successfully translated via external service: {}", maResponse);
        return Objects.requireNonNull(maResponse.getData(), "Response data cannot be null");
    }

    private HttpResponse<String> executeWithRetry(HttpRequest request, URI uri) {
        int retryCount = 0;
        long backoffTime = DEFAULT_BACKOFF_TIME;

        while (retryCount <= MAX_RETRIES) {
            try {
                var response = httpClient.execute(client ->
                        client.send(request, HttpResponse.BodyHandlers.ofString()));

                if (response != null) {
                    log.debug("<< POST {} {}", uri, response.statusCode());
                } else {
                    log.debug("<< POST {} -1", uri);
                }

                if (response != null && response.statusCode() == 200) {
                    return response;
                }

                if (retryCount >= MAX_RETRIES) {
                    throwMaxRetriesException(response);
                }

                backoffTime = sleep(backoffTime);
            } catch (Exception e) {
                if (retryCount >= MAX_RETRIES) {
                    throw new RuntimeException("Failed after " + MAX_RETRIES + " retries", e);
                }
                backoffTime = sleep(backoffTime);
            }
            retryCount++;
        }

        throw new RuntimeException("Unexpected error: exceeded retry loop without return");
    }

    private void throwMaxRetriesException(HttpResponse<String> response) {
        if (response != null) {
            throw new RuntimeException("HTTP %d: %s".formatted(response.statusCode(), response.body()));
        } else {
            throw new RuntimeException("Failed to get response after " + MAX_RETRIES + " retries");
        }
    }

    private MachinumAutomataResponse parseResponse(HttpResponse<String> response) {
        return objectMapper.execute(mapper ->
                mapper.readValue(response.body(), MachinumAutomataResponse.class));
    }

    private HttpRequest createHttpRequest(String sourceText, URI uri) {
        var translateRequest = TranslateRequest.builder()
                .params(TranslateRequest.Params.builder()
                        .sourceText(sourceText)
                        .build())
                .build();

        var requestBody = objectMapper.execute(mapper -> mapper.writeValueAsString(translateRequest));

        log.debug(">> POST {}", uri);

        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                //10min
                .timeout(Duration.ofSeconds(600))
                .build();
    }

    private long sleep(long backoffTime) {
        // Wait before retrying with incremental backoff
        JavaUtil.sleep(backoffTime);
        return backoffTime * 2;
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class TranslateRequest {

        private Params params;

        @Data
        @AllArgsConstructor
        @Builder(toBuilder = true)
        @NoArgsConstructor(access = AccessLevel.PUBLIC)
        public static class Params {

            @JsonProperty("source_text")
            private String sourceText;

        }

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class MachinumAutomataResponse {

        @ToString.Include
        private boolean success;
        private TranslateResponse data;
        private String videoFile;
        @ToString.Include
        private int executionTime;

    }

    /* ============= */

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class TranslateResponse {

        @JsonProperty("translated_text")
        private String translatedText;

    }

}