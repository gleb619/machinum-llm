package machinum.processor.client;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class GeminiClientPool {

    private final List<GeminiClientItem> clients;

    private final AtomicInteger currentIndex = new AtomicInteger(0);

    public GeminiClientItem getAvailableClient() {
        for (int i = 0; i < clients.size(); i++) {
            int index = currentIndex.getAndIncrement() % clients.size();
            var client = clients.get(index);
            if (client.isAvailable()) {
                return client;
            }
        }

        throw new AppIllegalStateException("No available Gemini clients. All clients are quota-limited.");
    }

    public void handleQuotaError(GeminiClientItem client, String errorMessage) {
        long retryDelay = extractRetryDelay(errorMessage);
        client.blockClient(retryDelay);
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
        } catch (Exception e) {
            // Fallback delay
        }

        return 60; // Default 1 minute if can't parse
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class GeminiClientItem {

        private final Client client;

        private LocalDateTime blockedUntil;
        private boolean isBlocked;

        public <T> T execute(Function<Client, T> function) {
            return function.apply(client);
        }

        public boolean isAvailable() {
            if (!isBlocked) return true;
            if (blockedUntil != null && LocalDateTime.now().isAfter(blockedUntil)) {
                isBlocked = false;
                blockedUntil = null;
                return true;
            }

            return false;
        }

        public void blockClient(long retryDelaySeconds) {
            this.isBlocked = true;
            this.blockedUntil = LocalDateTime.now().plusSeconds(retryDelaySeconds);
        }

    }


}
