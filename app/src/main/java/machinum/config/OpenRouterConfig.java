package machinum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import machinum.processor.client.OpenRouterChatClientPool;
import machinum.util.TraceUtil;
import org.apache.commons.io.IOUtils;
import org.springframework.ai.autoconfigure.ollama.OpenRouterAiChatProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.async.AsyncHelper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties({
        OpenRouterAiChatProperties.class,
        OpenAiConnectionProperties.class,
        OpenAiChatProperties.class,
})
public class OpenRouterConfig {

    public static final Pattern PARAMETERS_PATTERN = Pattern.compile("(\\d+)b");

    @Bean
    public OpenRouterChatClientPool openRouterChatClientPool(
            OpenAiConnectionProperties connectionProperties,
            OpenAiChatProperties chatProperties,
            WebClient.Builder webClientBuilder,
            RetryTemplate retryTemplate,
            OpenRouterAiChatProperties multiChatProperties,
            Holder<WiremockRequestInterceptor> requestInterceptorHolder
    ) {
        Set<String> models;

        if (multiChatProperties.getOptions().isDynamicMode()) {
            log.info("OpenRouter mode: dynamic - fetching models from API");
            models = fetchDynamicModels(connectionProperties, multiChatProperties);
        } else {
            log.info("OpenRouter mode: static - using configured models");
            models = multiChatProperties.getOptions().acquireModels();
        }

        var observationRegistry = ObservationRegistry.NOOP;
        log.info("Prepare to build OpenRouterPool with {} models", models.size());

        return new OpenRouterChatClientPool(
                models.stream()
                        .map(model -> {
                            var chatOptions = OpenAiChatOptions.fromOptions(chatProperties.getOptions());
                            chatOptions.setModel(model);

                            var openAiApi = createApi(connectionProperties, chatProperties, webClientBuilder, requestInterceptorHolder.get());

                            var openAiChatModel = new OpenAiChatModel(
                                    openAiApi,
                                    chatOptions,
                                    DefaultToolCallingManager.builder().observationRegistry(observationRegistry).build(),
                                    retryTemplate,
                                    observationRegistry);

                            // Create ChatClient with similar configuration to original service
                            var client = ChatClient.builder(openAiChatModel)
                                    .defaultAdvisors(
                                            new SimpleLoggerAdvisor())
                                    .build();

                            return new OpenRouterChatClientPool.OpenRouterClientItem(client, model);
                        })
                        .collect(Collectors.toList()));
    }

    private Set<String> fetchDynamicModels(OpenAiConnectionProperties connectionProperties, OpenRouterAiChatProperties properties) {
        try {
            String baseUrl = "https://openrouter.ai/api/frontend";

            // Create RestTemplate for API calls
            var restTemplate = new RestTemplate();
            restTemplate.setErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);

            // Set up headers
            var headers = new HttpHeaders();
            //headers.set("Authorization", "Bearer " + connectionProperties.getApiKey());
            headers.set("Content-Type", "application/json");
            headers.set("X-Title", "machinum-llm");

            var entity = new HttpEntity<String>(headers);

            log.debug("Fetching models from OpenRouter API: {}", baseUrl + "/models");

            var response = restTemplate.exchange(
                    baseUrl + "/models/find?context=32000&max_price=0",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var objectMapper = new ObjectMapper();
                var root = objectMapper.readTree(response.getBody());
                var modelsArray = root.path("data").path("models");

                var filteredModels = new LinkedHashSet<String>();

                if (modelsArray.isArray()) {
                    for (var model : modelsArray) {
                        var slug = model.path("slug").asText();
                        if (!slug.isEmpty() && isLargeParameterModel(slug)) {
                            filteredModels.add(slug);
                        }
                    }
                }

                log.info("Fetched {} models from OpenRouter, filtered to {} large-parameter models",
                        modelsArray.size(), filteredModels.size());

                if (filteredModels.isEmpty()) {
                    log.warn("No large-parameter models found, falling back to static mode");
                    return properties.getOptions().acquireModels();
                } else {
                    log.info("Working with next models: {}", filteredModels);
                }

                return filteredModels;
            } else {
                log.warn("Failed to fetch models from OpenRouter API, status: {}, falling back to static mode",
                        response.getStatusCode());
                return properties.getOptions().acquireModels();
            }

        } catch (Exception e) {
            log.error("Exception while fetching dynamic models from OpenRouter, falling back to static mode", e);
            return properties.getOptions().acquireModels();
        }
    }

    private boolean isLargeParameterModel(String modelSlug) {
        // Extract parameter count from model slug using regex
        // Look for patterns like "8b", "70b", "405b", etc.
        var matcher = PARAMETERS_PATTERN.matcher(modelSlug.toLowerCase());

        if (matcher.find()) {
            try {
                int paramCount = Integer.parseInt(matcher.group(1));
                return paramCount >= 16;
            } catch (NumberFormatException e) {
                log.debug("Could not parse parameter count from model: {}", modelSlug);
                return false;
            }
        }

        log.debug("No parameter count found in model: {}", modelSlug);
        return true;
    }

    private OpenAiApi createApi(OpenAiConnectionProperties connectionProperties,
                                OpenAiChatProperties chatProperties,
                                WebClient.Builder webClientBuilder,
                                WiremockRequestInterceptor requestInterceptorHolder) {
        var clientHttpRequestFactory = new JdkClientHttpRequestFactory();
        clientHttpRequestFactory.setReadTimeout(Duration.ofMinutes(15));

        var headers = new HttpHeaders();
//        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
        headers.set("X-Title", "machinum-llm");

        var builder = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(clientHttpRequestFactory))
                .requestInterceptors(list -> list.add((request, body, execution) -> {
                    var rayId = TraceUtil.getCurrentRayId();

                    log.debug("[{}] >> {} {}", rayId, request.getMethod(), request.getURI());
                    request.getHeaders().add("X-Ray-Id", rayId);
                    var response = execution.execute(request, body);
                    log.debug("[{}] << {} {} {}", rayId, request.getMethod(), request.getURI(), response.getStatusCode());
                    if (log.isTraceEnabled()) {
                        var responseBody = new String(IOUtils.toByteArray(response.getBody()), StandardCharsets.UTF_8);
                        log.debug("BODY: {}", responseBody.replaceAll("[\r\n]", " ").replaceAll("\\s+", " "));
                    }

                    return response;
                }))
                .defaultHeaders(h -> h.addAll(headers))
                .requestInterceptor(requestInterceptorHolder);

        var apiKey = connectionProperties.getApiKey();

        return OpenAiApi.builder()
                .baseUrl(chatProperties.getBaseUrl() != null ? chatProperties.getBaseUrl() : connectionProperties.getBaseUrl())
                .apiKey(apiKey)
                .headers(headers)
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .restClientBuilder(builder)
                .webClientBuilder(webClientBuilder)
                .responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
                .build();
    }

    @Bean
    public CommandLineRunner commandLineRunner(OpenRouterChatClientPool clientPool,
                                               OpenAiConnectionProperties connectionProperties,
                                               OpenAiChatProperties chatProperties,
                                               AsyncHelper asyncHelper) {
        return args -> {
            var apiUrl = chatProperties.getBaseUrl() != null ? chatProperties.getBaseUrl() : connectionProperties.getBaseUrl();
            var apiKey = connectionProperties.getApiKey();
            asyncHelper.runAsync(() -> clientPool.init(apiUrl, apiKey));
        };
    }

}
