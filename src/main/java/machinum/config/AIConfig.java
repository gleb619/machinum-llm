package machinum.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import machinum.extract.util.NamedEntityRecognizer;
import machinum.extract.util.ProperNameExtractor;
import machinum.util.TraceUtil;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.springframework.ai.autoconfigure.ollama.OllamaReserveProperties;
import org.springframework.ai.autoconfigure.ollama.OllamaTransformProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.converter.AssistantMessageDeserializer;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.model.ollama.autoconfigure.OllamaInitializationProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import static machinum.config.Holder.of;
import static machinum.util.TextUtil.toShortDescription;
//import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
@EnableConfigurationProperties({OllamaTransformProperties.class, OllamaReserveProperties.class})
public class AIConfig {

//    @Bean
//    public EmbeddingModel embeddingModel() {
//        return OllamaEmbeddingModel.builder()
//                .build();
//    }

//    @Bean
//    VectorStore vectorStore(JdbcTemplate template, EmbeddingModel embeddingModel) {
//        return PgVectorStore.builder(template, embeddingModel)
//                .build();
//    }

    private static OllamaApi createApi(OllamaConnectionDetails connectionDetails,
                                       RestClientBuilderConfigurer restClientBuilderConfigurer,
                                       ObjectProvider<WebClient.Builder> webClientBuilderProvider,
                                       WiremockRequestInterceptor requestInterceptorHolder) {
        var clientHttpRequestFactory = new JdkClientHttpRequestFactory();
        clientHttpRequestFactory.setReadTimeout(Duration.ofMinutes(10));

        var builder = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(clientHttpRequestFactory))
                .requestInterceptors(list -> list.add((request, body, execution) -> {
                    var rayId = TraceUtil.getCurrentRayId();

                    log.debug("[{}] >> {} {}", rayId, request.getMethod(), request.getURI());
                    request.getHeaders().add("X-Ray-Id", rayId);
                    var response = execution.execute(request, body);
                    log.debug("[{}] << {} {} {}", rayId, request.getMethod(), request.getURI(), response.getStatusCode());

                    return response;
                }))
                .requestInterceptor(requestInterceptorHolder);

        return OllamaApi.builder().baseUrl(connectionDetails.getBaseUrl())
                .restClientBuilder(restClientBuilderConfigurer.configure(builder))
                .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
                .responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
                .build();
    }

    @Bean
    @Deprecated
    public Holder<OllamaApi> ollamaReserveApiHolder(OllamaReserveProperties connectionDetails, RestClientBuilderConfigurer restClientBuilderConfigurer,
                                                    ObjectProvider<WebClient.Builder> webClientBuilderProvider, Holder<WiremockRequestInterceptor> requestInterceptorHolder) {
        return of(createApi(connectionDetails::getBaseUrl, restClientBuilderConfigurer, webClientBuilderProvider, requestInterceptorHolder.get()));
    }

    @Bean
    @Deprecated
    public Holder<OllamaChatModel> ollamaReserveChatModelHolder(Holder<OllamaApi> ollamaApiHolder, OllamaReserveProperties properties,
                                                                OllamaInitializationProperties initProperties,
                                                                ToolCallingManager toolCallingManager,
                                                                ObjectProvider<ObservationRegistry> observationRegistry,
                                                                ObjectProvider<ChatModelObservationConvention> observationConvention) {
        PullModelStrategy chatModelPullStrategy = initProperties.getChat().isInclude() ? initProperties.getPullModelStrategy() : PullModelStrategy.NEVER;
        OllamaChatModel chatModel = OllamaChatModel.builder().ollamaApi(ollamaApiHolder.data())
                .defaultOptions(properties.getOptions())
                .toolCallingManager(toolCallingManager)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .modelManagementOptions(new ModelManagementOptions(chatModelPullStrategy,
                        initProperties.getChat().getAdditionalModels(), initProperties.getTimeout(),
                        initProperties.getMaxRetries()))
                .build();
        Objects.requireNonNull(chatModel);
        observationConvention.ifAvailable(chatModel::setObservationConvention);

        return of(chatModel);
    }

//    @Bean
//    public TokenTextSplitter tokenTextSplitter() {
//        return new TokenTextSplitter();
//    }

    @Bean
    @Primary
    public OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails, RestClientBuilderConfigurer restClientBuilderConfigurer,
                               ObjectProvider<WebClient.Builder> webClientBuilderProvider, Holder<WiremockRequestInterceptor> requestInterceptorHolder) {
        return createApi(connectionDetails, restClientBuilderConfigurer, webClientBuilderProvider, requestInterceptorHolder.get());
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(
                                request -> toShortDescription(ModelOptionsUtils
                                        .toJsonString(request), 150),
                                response -> toShortDescription(ModelOptionsUtils
                                        .toJsonString(response), 150),
                                99
                        )
                )
//                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, request))
                .build();
    }

    @Bean
    public Holder<ObjectMapper> objectMapperHolder(Jackson2ObjectMapperBuilder builder) {
        Supplier<ObjectMapper> creator = () -> builder
                .createXmlMapper(false)
                .featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
//                .modules(assistantMessageModule())
                .build();

        return new Holder<>(creator.get(), mapper -> creator.get());
    }

    @Bean
    public Holder<OllamaChatProperties> ollamaChatPropertiesHolder(OllamaChatProperties ollamaChatProperties) {
        return of(ollamaChatProperties);
    }

    @Bean
    public Holder<OllamaConnectionProperties> ollamaConnectionPropertiesHolder(OllamaConnectionProperties properties) {
        return of(properties);
    }

    @Bean
    @Deprecated
    public Holder<OllamaTransformProperties> ollamaTransformPropertiesHolder(OllamaTransformProperties ollamaChatProperties) {
        return of(ollamaChatProperties);
    }

    @Bean
    public Holder<WiremockRequestInterceptor> wiremockRequestInterceptor(Holder<ObjectMapper> objectMapperHolder,
                                                                         @Value("${app.http.logs-path}") String logsPath,
                                                                         @Value("${app.http.logs-enabled}") Boolean logsEnabled,
                                                                         AsyncHelper asyncHelper) {
        var logsDir = Paths.get(logsPath);
        logsDir.toFile().mkdirs();

        return of(new WiremockRequestInterceptor(logsEnabled, objectMapperHolder.get(), logsDir.toAbsolutePath(), asyncHelper));
    }

    @Bean
    public SimpleModule assistantMessageModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(AssistantMessage.class, new AssistantMessageDeserializer());

        return module;
    }

    @Bean
    public NamedEntityRecognizer namedEntityRecognizer(
            @Value("classpath:nlp-models/en-ner-location.bin") Resource locationResource,
            @Value("classpath:nlp-models/en-ner-organization.bin") Resource organizationResource,
            @Value("classpath:nlp-models/en-ner-person.bin") Resource personResource) {

        Function<Resource, NameFinderME> creator = resource -> {
            try (var modelIn = resource.getInputStream()) {
                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                return new NameFinderME(model);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Map<String, NameFinderME> modelPaths = new HashMap<>();
        modelPaths.put("PERSON", creator.apply(locationResource));
        modelPaths.put("LOCATION", creator.apply(organizationResource));
        modelPaths.put("ORGANIZATION", creator.apply(personResource));

        return new NamedEntityRecognizer(modelPaths);
    }

    @Bean
    public ProperNameExtractor properNameExtractor(NamedEntityRecognizer namedEntityRecognizer) {
        return new ProperNameExtractor(namedEntityRecognizer);
    }

    /* ============= */

    @Bean
    public Holder<RestTemplate> ollamaAiClientRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return of(restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(30))
                .build());
    }

}