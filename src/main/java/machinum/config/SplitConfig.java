package machinum.config;

import machinum.processor.core.SplitStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SplitConfig {

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "single")
    public SplitStrategy singleSplitter() {
        return new SplitStrategy.SingleSplitter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "springstandard")
    public SplitStrategy springStandardSplitter() {
        return new SplitStrategy.SpringStandardSplitter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "springmin")
    public SplitStrategy springMinSplitter() {
        return new SplitStrategy.SpringMinSplitter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "lines")
    public SplitStrategy linesSplitter(@Value("#{${spring.ai.ollama.chat.options.numCtx} * 0.1}") int maxTokensPerChunk) {
        return new SplitStrategy.LinesSplitter(maxTokensPerChunk);
    }

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "whitespaces")
    public SplitStrategy whiteSpacesSplitter() {
        return new SplitStrategy.WhiteSpacesSplitter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "balancedlines")
    public SplitStrategy BalancedLinesSplitter(@Value("#{${spring.ai.ollama.chat.options.numCtx} * 0.1}") int maxTokensPerChunk,
                                               @Value("${app.split.overlap-size}") int overlapSize) {
        return new SplitStrategy.BalancedLinesSplitter(maxTokensPerChunk, overlapSize);
    }

    @Bean
    @ConditionalOnProperty(name = "app.split.mode", havingValue = "balancedsentence")
    public SplitStrategy BalancedSentenceSplitter(@Value("${app.split.chunk-size:512}") int maxCharactersSize) {
        return new SplitStrategy.BalancedSentenceSplitter(maxCharactersSize);
    }

    @Bean
    @ConditionalOnMissingBean
    public SplitStrategy defaultSplitStrategy() {
        return SplitStrategy.defaults();
    }

}
