package machinum.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
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
        var request = createHttpRequest(sourceText, uri);

        HttpResponse<String> response = null;
        try {
            response = httpClient.execute(client -> client.send(request, HttpResponse.BodyHandlers.ofString()));
        } finally {
            if (Objects.nonNull(response)) {
                log.debug("<< POST {} {}", uri, response.statusCode());
            } else {
                log.debug("<< POST {} -1", uri);
            }
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP %d: %s".formatted(response.statusCode(), response.body()));
        }

        var finalResponse = response;
        var maResponse = objectMapper.execute(mapper -> mapper.readValue(finalResponse.body(), MachinumAutomataResponse.class));

        log.debug("Successfully translated via external service: {}", maResponse);

        return Objects.requireNonNull(maResponse.getData(), "Response can't be null or empty");
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
