package machinum.processor.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static machinum.util.JavaUtil.parseDuration;
import static machinum.util.JavaUtil.sleep;

@Slf4j
@RequiredArgsConstructor
public class OpenRouterChatClientPool {

    private final List<OpenRouterClientItem> clients;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private HealthHelper.RateLimit rateLimit;

    public OpenRouterChatClientPool init(String apiUrl, String apiKey) {
        var result = new HealthHelper().check(apiUrl, apiKey);

        if (result == null || result.getData() == null) {
            throw new AppIllegalStateException("Invalid API key or API is not available");
        }

        var data = result.getData();

        // Check if account has negative balance (will cause 402 errors)
        if (data.getLimit() > 0 && data.getUsage() >= data.getLimit()) {
            log.warn("API key has reached credit limit. Usage: {}, Limit: {}", data.getUsage(), data.getLimit());
        }

        // Log rate limit information
        if (data.getRateLimit() != null) {
            log.info("Openrouter Rate limit: {} requests per {}", data.getRateLimit().getRequests(), data.getRateLimit().getInterval());
            this.rateLimit = data.getRateLimit();
        }

        // Log free tier limitations
        if (data.isFreeTier()) {
            log.info("Using free tier API key with daily limits");
            if (data.getUsage() < 10) {
                log.info("Free tier limited to 50 requests per day (less than 10 credits purchased)");
            } else {
                log.info("Free tier limited to 1000 requests per day (at least 10 credits purchased)");
            }
        } else {
            log.warn("Paid tier is enabled, be careful of API costs!");
        }

        return this;
    }

    public OpenRouterClientItem getAvailableClient() {
        // First, try to use the current client if it's available
        var currentClient = clients.get(currentIndex.get());
        if (currentClient.isAvailable()) {
            return currentClient;
        }

        // Move to next client in rotation
        currentIndex.getAndIncrement();

        // First pass: check for immediately available clients
        for (int i = 0; i < clients.size(); i++) {
            int index = currentIndex.getAndIncrement() % clients.size();
            var client = clients.get(index);
            if (client.isAvailable()) {
                return client;
            }
        }

        // Second pass: find client with shortest remaining block time
        OpenRouterClientItem nextAvailable = null;
        LocalDateTime earliestUnblock = null;

        for (var client : clients) {
            if (client.getBlockedUntil() != null) {
                if (earliestUnblock == null || client.getBlockedUntil().isBefore(earliestUnblock)) {
                    earliestUnblock = client.getBlockedUntil();
                    nextAvailable = client;
                }
            }
        }

        if (nextAvailable != null) {
            long waitSeconds = Duration.between(LocalDateTime.now(), earliestUnblock).getSeconds();
            log.warn("All clients rate limited. Next available in {} seconds", Math.max(0, waitSeconds));

            waitUntilFree(waitSeconds);

            // Try again after waiting
            return getAvailableClient();
        }

        throw new AppIllegalStateException("No available OpenRouter clients. All clients are rate-limited.");
    }

    public void handleRateLimitError(OpenRouterClientItem client, String errorMessage) {
        long retryDelay = extractRetryDelay(errorMessage);
        client.blockClient(retryDelay);
        log.warn("Client blocked due to rate limit. Retry in {} seconds", retryDelay);
    }

    public void handle402Error(OpenRouterClientItem client) {
        // Block client for longer duration on 402 errors (negative balance)
        client.blockClient(300); // 5 minutes
        log.error("Client blocked due to 402 error (negative balance). Add credits to continue using API");
    }

    public void handleDDoSProtection(OpenRouterClientItem client) {
        // Block client for extended period on DDoS protection trigger
        client.blockClient(600); // 10 minutes
        log.error("Client blocked due to DDoS protection. Reduce request frequency");
    }

    public int availableSize() {
        return (int) clients.stream().filter(OpenRouterClientItem::isAvailable).count();
    }

    private long extractRetryDelay(String errorMessage) {
        try {
            if (errorMessage.contains("retryDelay")) {
                var parts = errorMessage.split("retryDelay\": \"");
                if (parts.length > 1) {
                    String delayPart = parts[1].split("s\"")[0];
                    return Long.parseLong(delayPart);
                }
            }

            // Check for other rate limit indicators
            if (errorMessage.contains("rate limit") || errorMessage.contains("requests per minute")) {
                return 60; // 1 minute for general rate limits
            }

            if (errorMessage.contains("Too Many Requests")) {
                return 30; // 30 seconds for 429 errors
            }

        } catch (Exception e) {
            log.warn("Failed to parse retry delay from error message: {}", errorMessage);
        }

        if (Objects.nonNull(rateLimit)) {
            return rateLimit.interval().toSeconds();
        }

        return 10; // Default 10 seconds if can't parse
    }

    private void waitUntilFree(long waitSeconds) {
        log.warn("Waiting for {} seconds", waitSeconds);
        sleep(Math.max(0, waitSeconds) * 1000L);
    }

    @Getter
    @ToString
    @RequiredArgsConstructor
    public static class OpenRouterClientItem {

        @ToString.Exclude
        private final ChatClient client;
        private final String model;

        private LocalDateTime blockedUntil;
        private boolean isBlocked;

        public <T> T execute(BiFunction<ChatClient, String, T> function) {
            if (!isAvailable()) {
                throw new AppIllegalStateException("Client is currently blocked due to rate limits");
            }
            return function.apply(client, model);
        }

        public boolean isAvailable() {
            if (!isBlocked) return true;
            if (blockedUntil != null && LocalDateTime.now().isAfter(blockedUntil)) {
                unblockClient();
                return true;
            }

            log.debug("Client is still blocked, time to wait: {}s", getSecondsUntilAvailable());
            return false;
        }

        public void blockClient(long retryDelaySeconds) {
            this.isBlocked = true;
            this.blockedUntil = LocalDateTime.now().plusSeconds(retryDelaySeconds);
            log.debug("Client blocked until: {}, model={}", blockedUntil, model);
        }

        private void unblockClient() {
            this.isBlocked = false;
            this.blockedUntil = null;
            log.debug("Client unblocked and available for use: {}", model);
        }

        public long getSecondsUntilAvailable() {
            if (!isBlocked || blockedUntil == null) return 0;
            return Math.max(0, Duration.between(LocalDateTime.now(), blockedUntil).getSeconds());
        }
    }

    public static class HealthHelper {

        @SneakyThrows
        public KeyResponse check(String apiUrl, String apiKey) {
            URI uri = URI.create(apiUrl + "/v1/key");

            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            var request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "OpenRouter-Java-Client/1.0")
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var objectMapper = createObjectMapper();
                return objectMapper.readValue(response.body(), KeyResponse.class);
            } else if (response.statusCode() == 401) {
                log.error("Authentication failed: Invalid API key");
                throw new AppIllegalStateException("Invalid API key");
            } else if (response.statusCode() == 402) {
                log.error("Payment required: Negative credit balance");
                throw new AppIllegalStateException("Negative credit balance - add credits to continue");
            } else if (response.statusCode() == 429) {
                log.error("Rate limit exceeded during health check");
                throw new AppIllegalStateException("Rate limit exceeded");
            } else {
                log.error("Failed to fetch key data. Status code: {}, body={}", response.statusCode(), response.body());
                throw new AppIllegalStateException("API health check failed with status: " + response.statusCode());
            }
        }

        private ObjectMapper createObjectMapper() {
            return new ObjectMapper()
                    .findAndRegisterModules()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        @Data
        @AllArgsConstructor
        @Builder(toBuilder = true)
        @NoArgsConstructor(access = AccessLevel.PUBLIC)
        public static class KeyResponse {

            private DataInfo data;

        }

        @Data
        @AllArgsConstructor
        @Builder(toBuilder = true)
        @NoArgsConstructor(access = AccessLevel.PUBLIC)
        public static class DataInfo {

            private String label;

            @JsonProperty("limit")
            private Integer limit;

            private int usage;

            @JsonAlias("is_provisioning_key")
            private boolean isProvisioningKey;

            @JsonAlias("limit_remaining")
            private int limitRemaining;

            @JsonAlias("is_free_tier")
            private boolean isFreeTier;

            @JsonAlias("rate_limit")
            private RateLimit rateLimit;

        }

        @Data
        @AllArgsConstructor
        @Builder(toBuilder = true)
        @NoArgsConstructor(access = AccessLevel.PUBLIC)
        public static class RateLimit {

            private int requests;
            private String interval;

            public Duration interval() {
                return parseDuration(interval);
            }

        }
    }

}