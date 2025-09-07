package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.Chunks;
import machinum.flow.model.Chunks.ChunkItem;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.processor.core.SplitStrategy;
import machinum.processor.core.SplitStrategy.BalancedSentenceSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class Splitter {

    @Value("${app.logic-splitter.chunk-size}")
    protected final Integer chunkSize;

    private final SplitStrategy splitStrategy;


    public FlowContext<Chapter> optionalSplit(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        var textTokens = countTokens(text);

        if (textTokens > (chunkSize * 1.6)) {
            log.debug("Text is large and will be divided into chunks: {}", flowContext);
            return split(flowContext);
        } else {
            return flowContext;
        }
    }

    public FlowContext<Chapter> split(FlowContext<Chapter> flowContext) {
        var text = flowContext.text();
        var list = work(text, chunkSize);
        return flowContext.rearrange(FlowContext::chunksArg, FlowContextActions.chunks(Chunks.of(list)));
    }

    public List<ChunkItem> work(String text, Integer chunkSize) {
        log.debug("Prepare to split text: text={}...", toShortDescription(text));
        var textTokens = countTokens(text);

        if (textTokens <= (chunkSize * 1.6)) {
            log.debug("Text is too small, will return one piece");
            return List.of(ChunkItem.of(text));
        }

        if (splitStrategy instanceof BalancedSentenceSplitter strategy) {
            var iterator = new AtomicInteger(1);
            var parts = Math.max((int) Math.ceil((double) textTokens / chunkSize), 2);
            var list = strategy.split(text, parts).stream()
                    .filter(Predicate.not(String::isBlank))
                    .map(ChunkItem::of)
                    .peek(chunkItem -> chunkItem.setPart(iterator.getAndIncrement()))
                    .toList();

            return list;
        } else {
            throw new IllegalArgumentException("Unknown split strategy: " + splitStrategy);
        }
    }

}
