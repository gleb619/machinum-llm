package machinum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import machinum.processor.core.GeminiClient.JacksonJsonParser;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public JacksonJsonParser jacksonJsonParserGenAi(@Qualifier("objectMapperHolder") Holder<ObjectMapper> mapperHolder) {
        return new JacksonJsonParser(mapperHolder.copy());
    }

}
