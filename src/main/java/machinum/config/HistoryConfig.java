package machinum.config;

import machinum.processor.core.HistoryStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HistoryConfig {

    @Bean
    @ConditionalOnProperty(name = "app.history.mode", havingValue = "chunks")
    public HistoryStrategy chunksHistory() {
        return new HistoryStrategy.ChunksStrategy();
    }

    @Bean
    @ConditionalOnProperty(name = "app.history.mode", havingValue = "chunkswithmessage")
    public HistoryStrategy chunksWithFullMessageHistory() {
        return new HistoryStrategy.ChunksWithFullMessageStrategy();
    }

    @Bean
    @ConditionalOnProperty(name = "app.history.mode", havingValue = "message")
    public HistoryStrategy fullMessageHistory() {
        return new HistoryStrategy.FullMessageStrategy();
    }

    @Bean
    @ConditionalOnProperty(name = "app.history.mode", havingValue = "makeupatext")
    public HistoryStrategy makeupTextHistory() {
        return new HistoryStrategy.MakeupTextStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public HistoryStrategy defaultHistoryStrategy() {
        return HistoryStrategy.defaults();
    }

}
