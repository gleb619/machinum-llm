package machinum.extract;

import machinum.flow.FlowArgument;
import machinum.flow.FlowContext;
import machinum.flow.FlowSupport;
import machinum.model.Chapter;
import machinum.processor.core.ChunkSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static machinum.flow.FlowContext.result;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.countTokens;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class SSMLConverter implements ChunkSupport, FlowSupport {

    @Value("${app.ssml.chunkSize}")
    protected final Integer chunkSize;

    private final SSMLSerializer ssmlSerializer;

    private final Splitter splitter;

    public FlowContext<Chapter> convert(FlowContext<Chapter> flowContext) {
        var counter = new AtomicInteger(1);

        var items = splitter.work(flowContext.translatedText(), chunkSize);
        var rawSSMLText = items.stream()
                .map(chunk -> ssmlSerializer.convert(
                        flowContext.replace(FlowContext::translatedTextArg, FlowContext.translatedText(chunk.getText()))
                                .rearrange(FlowContext::subIterationArg, FlowContext.subIteration(counter.getAndIncrement()))
                ))
                .map(FlowContext::resultArg)
                .map(FlowArgument::stringValue)
                .collect(Collectors.joining("\n"));

        var ssmlText = joinSSML(rawSSMLText);

        System.out.println("SSMLConverter.convert: " + ssmlText);

        return flowContext.rearrange(FlowContext::resultArg, result(ssmlText));
    }

    private String joinSSML(String ssmlText) {
        return """
                <speak>
                %s
                </speak>
                """.formatted(
                ssmlText.replace("<speak>", "")
                        .replace("</speak>", "")
        );
    }

}
