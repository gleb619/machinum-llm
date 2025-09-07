package machinum.processor.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.processor.core.AssistantContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static machinum.util.JavaUtil.sleep;

/**
 * Client implementation for interacting with OpenRouter AI services.
 * This class manages multiple client connections and handles retry logic
 * with different error handling strategies based on the type of error received.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterAiClient implements AiClient {

    private final OpenRouterChatClientPool clientPool;

    /**
     * Executes a chat request using the OpenRouter AI service.
     *
     * @param assistantContext The context for the assistant's response
     * @param prompt           The prompt to be sent to the AI service
     * @return The assistant's response message
     * @throws AppIllegalStateException if all retry attempts fail or if there's a non-recoverable error
     */
    @Override
    public AssistantMessage call(AssistantContext assistantContext, Prompt prompt) {
        var localPrompt = prompt.copy();
        log.info("|-->> Executing OpenRouter request with context: {}", assistantContext);
        Exception lastException = null;
        OpenRouterChatClientPool.OpenRouterClientItem currentClient = null;

        int clientsSize = clientPool.availableSize();
        for (int attempt = 0; attempt < clientsSize; attempt++) {
            try {
                currentClient = clientPool.getAvailableClient();

                var spec = currentClient.execute((client, model) -> {
                    ChatOptions options = localPrompt.getOptions();
                    if (options instanceof OllamaOptions oo) {
                        log.warn("Override prompt settings, set model to: {}", model);
                        oo.setModel(model);
                    }

                    return client.prompt(localPrompt);
                });

                log.trace("Executing request: {}", assistantContext);
                return executeOriginRequest(spec);

            } catch (Exception e) {
                log.error("|X-- Error during OpenRouter execution (attempt {}): {}|{}",
                        attempt + 1, e.getClass().getSimpleName(), e.getMessage());
                lastException = e;
                String errorMessage = e.getMessage();

                if (currentClient != null) {
                    handleErrorByType(currentClient, e, errorMessage);
                }

                if (attempt == clientsSize - 1) {
                    break;
                }

                sleep(1000);
            }
        }

        String finalMessage = "Failed to get response after %d attempts".formatted(clientsSize);
        log.error("|X-- {}", finalMessage);

        throw new AppIllegalStateException(finalMessage, lastException);
    }

    /**
     * Returns the provider type for this AI client.
     *
     * @return The Provider enum value representing OpenRouter
     */
    @Override
    public Provider getProvider() {
        return Provider.OPENROUTER;
    }

    /* ============= */

    /**
     * Executes the original request to the chat client.
     *
     * @param spec The request specification for the chat client.
     * @return The response message from the AI assistant.
     * @throws AppIllegalStateException if there's an error executing the request
     */
    private AssistantMessage executeOriginRequest(ChatClient.ChatClientRequestSpec spec) {
        try {
            var call = spec.call();
            var response = call.chatResponse();
            var output = parseAssistantMessage(response);

            log.debug("Successfully received response from OpenRouter API");
            return output;

        } catch (Exception e) {
            log.error("Error executing OpenRouter request: {}", e.getMessage());
            throw e; // Re-throw to be handled by the retry logic
        }
    }

    /**
     * Parses the ChatResponse to extract the AssistantMessage.
     *
     * @param response The chat response from the AI service
     * @return The parsed AssistantMessage
     * @throws AppIllegalStateException if the response or its components are null
     */
    private AssistantMessage parseAssistantMessage(ChatResponse response) {
        if (response == null) {
            throw new AppIllegalStateException("Received null response from OpenRouter API");
        }

        var result = response.getResult();
        if (result == null) {
            throw new AppIllegalStateException("Received null result from OpenRouter API");
        }

        var output = result.getOutput();
        if (output == null) {
            throw new AppIllegalStateException("Received null output from OpenRouter API");
        }

        return output;
    }

    /**
     * Handles different types of errors based on their characteristics.
     *
     * @param client       The client that encountered the error
     * @param e            The exception that occurred
     * @param errorMessage The error message string
     */
    private void handleErrorByType(OpenRouterChatClientPool.OpenRouterClientItem client, Exception e, String errorMessage) {
        if (is402Error(e, errorMessage)) {
            clientPool.handle402Error(client);
            log.error("|X-- 402 Payment Required: Negative credit balance detected");
        } else if (isDDoSProtectionError(e, errorMessage)) {
            clientPool.handleDDoSProtection(client);
            log.error("|X-- DDoS protection triggered: Request frequency too high");
        } else if (isRateLimitError(e, errorMessage)) {
            clientPool.handleRateLimitError(client, errorMessage);
            log.warn("|X-- Rate limit error: {}", errorMessage);
        } else if (isFreeModelLimitError(errorMessage)) {
            // Handle free model daily limits (20 req/min, 50-1000 req/day)
            clientPool.handleRateLimitError(client, errorMessage);
            log.warn("|X-- Free model limit exceeded: {}", errorMessage);
        } else if (isRetryableError(e)) {
            // Don't block client for retryable errors like network issues
            log.warn("|X-- Retryable error: {}", errorMessage);
        } else {
            // Non-rate-limit error, don't block client but still throw
            log.error("|X-- Non-recoverable error: {}", errorMessage);
            throw new AppIllegalStateException("OpenRouter API error: %s".formatted(errorMessage), e);
        }
    }

    /**
     * Checks if the exception represents a 402 Payment Required error.
     *
     * @param e            The exception to check
     * @param errorMessage The error message string
     * @return true if it's a 402 error, false otherwise
     */
    private boolean is402Error(Exception e, String errorMessage) {
        if (e instanceof HttpClientErrorException hce && hce.getStatusCode().value() == 402) return true;
        return errorMessage != null && (
                errorMessage.contains("402") ||
                        errorMessage.contains("Payment Required") ||
                        errorMessage.contains("negative balance") ||
                        errorMessage.contains("credit limit")
        );
    }

    /**
     * Checks if the exception represents a DDoS protection error.
     *
     * @param e            The exception to check
     * @param errorMessage The error message string
     * @return true if it's a DDoS protection error, false otherwise
     */
    private boolean isDDoSProtectionError(Exception e, String errorMessage) {
        return errorMessage != null && (
                errorMessage.contains("DDoS protection") ||
                        errorMessage.contains("Cloudflare") ||
                        errorMessage.contains("dramatically exceed") ||
                        errorMessage.contains("security check")
        );
    }

    /**
     * Checks if the exception represents a rate limit error.
     *
     * @param e            The exception to check
     * @param errorMessage The error message string
     * @return true if it's a rate limit error, false otherwise
     */
    private boolean isRateLimitError(Exception e, String errorMessage) {
        if (e instanceof HttpClientErrorException.TooManyRequests) return true;

        return errorMessage != null && (
                errorMessage.contains("rate limit") || errorMessage.contains("Rate Limit") ||
                        errorMessage.contains("quota") || errorMessage.contains("Quota") ||
                        errorMessage.contains("exceeded") || errorMessage.contains("Exceeded") ||
                        errorMessage.contains("429") || errorMessage.contains("Too Many Requests") ||
                        errorMessage.contains("requests per minute") ||
                        errorMessage.contains("retryDelay")
        );
    }

    /**
     * Checks if the error message represents a free model limit error.
     *
     * @param errorMessage The error message string
     * @return true if it's a free model limit error, false otherwise
     */
    private boolean isFreeModelLimitError(String errorMessage) {
        return errorMessage != null && (
                errorMessage.contains("free tier") ||
                        errorMessage.contains("free tier")
        );
    }

    /**
     * Checks if the exception represents a retryable error.
     *
     * @param e The exception to check
     * @return true if it's a retryable error, false otherwise
     */
    private boolean isRetryableError(Exception e) {
        return e instanceof HttpServerErrorException || // 5xx errors
                e.getCause() instanceof java.net.SocketTimeoutException ||
                e.getCause() instanceof java.net.ConnectException ||
                (e.getMessage() != null && (
                        e.getMessage().contains("timeout") ||
                                e.getMessage().contains("connection") ||
                                e.getMessage().contains("network")
                ));
    }

}