package machinum.processor.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SplitFactory {

    public static final String MAX_TOKENS_PER_CHUNK_PARAM = "maxTokensPerChunk";

    public static final String OVERLAP_SIZE_PARAM = "overlapSize";

    public static final String MAX_CHARACTERS_PER_CHUNK_PARAM = "maxCharactersPerChunk";

    public static final String OVERLAP_CHARACTERS_SIZE_PARAM = "overlapCharactersSize";


    @Value("#{${spring.ai.ollama.chat.options.numCtx} * 0.3}")
    protected final Integer contentWindow;

    @Value("${app.split.overlap-size}")
    protected final Integer overlapSize;

    public SplitStrategy getSplitStrategy(SplitFactory.Type type) {
        return getSplitStrategy(type, Map.of());
    }

    public SplitStrategy getSplitStrategy(SplitFactory.Type type, Map<String, Object> context) {
        return switch (type) {
            case SINGLE -> new SplitStrategy.SingleSplitter();
            case SPRING_STANDARD -> new SplitStrategy.SpringStandardSplitter();
            case SPRING_MIN -> new SplitStrategy.SpringMinSplitter();
//            case SPRING_CUSTOM -> new SplitStrategy.SpringCustomSplitter();
            case LINES -> new SplitStrategy.LinesSplitter(parse(context, MAX_TOKENS_PER_CHUNK_PARAM, contentWindow));
            case WHITE_SPACES -> new SplitStrategy.WhiteSpacesSplitter();
            case BALANCED_PART ->
                    new SplitStrategy.BalancedLinesSplitter(parse(context, MAX_TOKENS_PER_CHUNK_PARAM, contentWindow), parse(context, OVERLAP_SIZE_PARAM, overlapSize));
            case SENTENCE ->
                    new SplitStrategy.SentenceSplitter(parse(context, MAX_CHARACTERS_PER_CHUNK_PARAM, contentWindow));
            case BALANCED_SENTENCE ->
                    new SplitStrategy.BalancedSentenceSplitter(parse(context, MAX_CHARACTERS_PER_CHUNK_PARAM, contentWindow));
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private Integer parse(Map<String, Object> context, String key, Integer defaultValue) {
        return (Integer) context.getOrDefault(key, defaultValue);
    }

    public enum Type {

        SINGLE,
        SPRING_STANDARD,
        SPRING_MIN,
        SPRING_CUSTOM,
        LINES,
        WHITE_SPACES,
        BALANCED_PART,
        SENTENCE,
        BALANCED_SENTENCE,

    }

}
