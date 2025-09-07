package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.model.FlowArgument;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.processor.core.ChunkSupport;
import machinum.processor.core.FlowSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
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
                        flowContext.replace(FlowContext::translatedTextArg, FlowContextActions.translatedText(chunk.getText()))
                                .rearrange(FlowContext::subIterationArg, FlowContextActions.subIteration(counter.getAndIncrement()))
                ))
                .map(FlowContext::resultArg)
                .map(FlowArgument::stringValue)
                .collect(Collectors.joining("\n"));

        var ssmlText = joinSSML(rawSSMLText);

        System.out.println("SSMLConverter.convert: " + ssmlText);

        return flowContext.rearrange(FlowContext::resultArg, FlowContextActions.result(ssmlText));
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
