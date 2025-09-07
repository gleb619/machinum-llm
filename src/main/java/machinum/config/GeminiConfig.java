package machinum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import machinum.processor.client.GeminiAiClient;
import machinum.processor.client.GeminiClient.JacksonJsonParser;
import machinum.processor.client.GeminiClientPool;
import machinum.processor.client.GeminiClientPool.GeminiClientItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import swiss.ameri.gemini.api.GenAi;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "production")
public class GeminiConfig {

    @Bean
    public GenAi genAi(JacksonJsonParser jacksonJsonParser, @Value("${spring.ai.gemini.token}") String token) {
        return new GenAi(token, jacksonJsonParser);
    }

    @Bean
    public JacksonJsonParser jacksonJsonParserGenAi(@Qualifier("objectMapperHolder") Holder<ObjectMapper> mapperHolder) {
        return new JacksonJsonParser(mapperHolder.copy());
    }

    @Bean
    public GeminiClientPool googleGeminiClient(@Value("${spring.ai.gemini-ai.token}") String[] tokens) {
        var clients = Stream.of(tokens).map(apiKey ->
                        Client.builder().apiKey(apiKey).build())
                .map(GeminiClientItem::new)
                .collect(Collectors.toList());

        return new GeminiClientPool(clients);
    }

    @Bean
    public GeminiAiClient geminiAiClient(
            @Value("${spring.ai.gemini-ai.chat.options.model:gemini-2.0-flash-exp}") String model,
            GeminiClientPool clientPool) {
        return new GeminiAiClient(model, clientPool);
    }

}
