package machinum.config;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties({
        OpenRouterAiChatProperties.class,
        OpenAiConnectionProperties.class,
        OpenAiChatProperties.class,
})
public class OpenRouterConfig {

    @Bean
    public OpenRouterChatClientPool openRouterChatClientPool(
            OpenAiConnectionProperties connectionProperties,
            OpenAiChatProperties chatProperties,
            WebClient.Builder webClientBuilder,
            RetryTemplate retryTemplate,
            OpenRouterAiChatProperties multiChatProperties,
            Holder<WiremockRequestInterceptor> requestInterceptorHolder
    ) {
        var models = multiChatProperties.getOptions().acquireModels();

        var observationRegistry = ObservationRegistry.NOOP;

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
