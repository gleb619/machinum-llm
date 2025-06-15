package machinum.processor.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.exception.StopException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Client implementation for interacting with Ollama AI services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAiClient implements AiClient {

    private final ChatClient chatClient;
    private final Holder<OllamaChatProperties> ollamaChatProperties;
    private final Holder<OllamaConnectionProperties> ollamaConnectionProperties;
    private final Holder<RestTemplate> restTemplate;

    /**
     * Executes a request to the AI assistant and handles potential errors.
     *
     * @param assistantContext The context for the assistant interaction.
     * @param prompt           The prompt to send to the AI assistant.
     * @return The response message from the AI assistant.
     */
    @Override
    public AssistantMessage call(AssistantContext assistantContext, Prompt prompt) {
        var spec = chatClient.prompt(prompt);
        try {
            log.info("Executing request: {}", assistantContext);
            return executeOriginRequest(spec);
        } catch (Exception e) {
            log.error("Error executing origin request: %s; e=%s|%s".formatted(assistantContext, e.getClass(), e.getMessage()));
            if (onError(prompt, e, spec)) {
                try {
                    return executeOriginRequest(spec);
                } catch (Exception ex) {
                    ExceptionUtils.rethrow(StopException.create(e));
                    return null;
                }
            } else {
                ExceptionUtils.rethrow(e);
                return null;
            }
        }
    }

    /**
     * Returns the provider for this client, which is OLLAMA.
     *
     * @return The AI provider.
     */
    @Override
    public Provider getProvider() {
        return Provider.OLLAMA;
    }

    /**
     * Handles errors that occur during interaction with Ollama AI.
     *
     * @param prompt The prompt that caused the error.
     * @param e      The exception that was thrown.
     * @param spec   The request specification for the chat client.
     * @return True if the error was handled and a retry is attempted, false otherwise.
     */
    private boolean onError(Prompt prompt, Exception e, ChatClientRequestSpec spec) {
        var llamaError = e.getMessage().contains("llama runner process has terminated");
        var timedOutError = e instanceof ResourceAccessException rae && rae.getMessage().contains("Request timed out");
        if (timedOutError) {
            log.error("Ollama process has stalled, please restart ollama runner");
            var model = Optional.ofNullable(prompt.getOptions()).map(ChatOptions::getModel)
                    .orElseGet(() -> ollamaChatProperties.get().getModel());
            unloadModel(model);
            return true;
        }

        if (llamaError) {
            log.error("Ollama is turned off by llama.cpp error, please restart ollama runner");
            cleanRunner(prompt);
            return true;
        } else {
            ExceptionUtils.rethrow(e);
        }

        return false;
    }

    /**
     * Executes the original request to the chat client.
     *
     * @param spec The request specification for the chat client.
     * @return The response message from the AI assistant.
     */
    private AssistantMessage executeOriginRequest(ChatClientRequestSpec spec) {
        var call = spec.call();
        var response = call.chatResponse();
        var result = response.getResult();

        return result.getOutput();
    }

    /**
     * Cleans and restarts the Ollama runner by sending a simple prompt.
     *
     * @param prompt The original prompt that caused the error.
     */
    private void cleanRunner(Prompt prompt) {
        try {
            ChatOptions options = prompt.getOptions().copy();
            if (options instanceof OllamaOptions ollamaOptions) {
                ollamaOptions.setModel(ollamaChatProperties.get().getModel());
                ollamaOptions.setKeepAlive("0");
            }
            var simplePrompt = new Prompt("", options);
            chatClient.prompt(simplePrompt)
                    .call().chatResponse();
        } catch (Exception innerException) {
            log.error("Failed to send simple prompt: %s".formatted(innerException.getMessage()), innerException);
        }
    }

    public void unloadModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            log.warn("Model name is null or empty, cannot unload.");
            return;
        }

        var connectionProperties = ollamaConnectionProperties.get();
        String unloadUrl = connectionProperties.getBaseUrl() + "/api/generate";
        OllamaUnloadRequest unloadRequest = new OllamaUnloadRequest(modelName, 0);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OllamaUnloadRequest> entity = new HttpEntity<>(unloadRequest, headers);

        try {
            log.info("Attempting to unload model: {} from Ollama URL: {}", modelName, unloadUrl);
            OllamaUnloadResponse response = restTemplate.execute(t -> t.postForObject(unloadUrl, entity, OllamaUnloadResponse.class));
            if (response != null && response.isDone() && "unload".equalsIgnoreCase(response.getDoneReason())) {
                log.info("Successfully unloaded model: {}. Response: {}", modelName, response);
            } else {
                log.warn("Failed to unload model {} or unexpected response. Response: {}", modelName, response);
            }
        } catch (HttpClientErrorException e) {
            log.error("HTTP error unloading model {}: {} - {}", modelName, e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error unloading model {}: {}", modelName, e.getMessage(), e);
        }
    }

    /* ============= */

    // DTO for the unload request payload
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class OllamaUnloadRequest {
        private String model;
        @JsonProperty("keep_alive")
        private Integer keepAlive; // Ollama API expects integer 0 for unload
    }

    // DTO for the unload response (can be more detailed based on actual response)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class OllamaUnloadResponse {

        private String model;
        @JsonProperty("created_at")
        private String createdAt;
        private String response; // Should be empty for unload
        private boolean done;
        @JsonProperty("done_reason")
        private String doneReason; // Should be "unload"

    }


}