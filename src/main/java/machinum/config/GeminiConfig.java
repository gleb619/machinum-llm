package machinum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.processor.core.GeminiClient;
import machinum.processor.core.GeminiClient.JacksonJsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import swiss.ameri.gemini.api.GenAi;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "production")
public class GeminiConfig {

    @Bean
    public GenAi genAi(JacksonJsonParser jacksonJsonParser, @Value("${spring.ai.gemini.token}") String token) {
        return new GenAi(token, jacksonJsonParser);
    }

    @Bean
    public JacksonJsonParser jacksonJsonParserGenAi(Holder<ObjectMapper> mapperHolder) {
        return new JacksonJsonParser(mapperHolder.copy());
    }

}
