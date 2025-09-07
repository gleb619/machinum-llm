package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.processor.core.AssistantContext;
import machinum.util.CodeBlockExtractor;
import machinum.util.TextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static machinum.flow.FlowContextActions.text;
import static org.apache.commons.lang3.StringEscapeUtils.escapeXml;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslaterXmlBody extends AbstractTranslaterBody {

    @Getter
    @Value("classpath:prompts/custom/system/TranslateBodySystem.xml.ST")
    private final Resource systemTemplate;
    @Getter
    @Value("classpath:prompts/custom/TranslateBody.xml.ST")
    private final Resource translateTemplate;

    @Override
    protected String parseTranslatedText(AssistantContext.Result context) {
        if (context.entity() instanceof List<?> l && l.getFirst() instanceof Translation) {
            List<Translation> list = context.entity();
            return list.stream()
                    .map(Translation::translated)
                    .collect(Collectors.joining("\n"));
        }

        return context.result();
    }

    @Override
    protected AssistantContext.Result processAssistantResult(AssistantContext.Result result) {
        var rawText = result.result();
        var rawXml = CodeBlockExtractor.extract(rawText).trim();
        var mapResult = parseTextFromXml(rawXml, "translated",
                (id, translated) -> new Translation(id, "", translated));
        if (mapResult.isEmpty()) {
            throw new AppIllegalStateException("All titles are empty");
        }
        result.setEntity(mapResult);

        return result;
    }

    @Override
    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var textArg = flowContext.textArg();
        var counter = new AtomicInteger(1);
        var list = TextUtil.toParagraphs(textArg.stringValue()).stream()
                .map(s -> new Translation(counter.getAndIncrement(), escapeXml(s), ""))
                .collect(Collectors.toList());
        var newText = convertToXml(list, "translated", Translation::translated);

        return super.translate(flowContext.replace(FlowContext::textArg, text(newText)))
                .replace(FlowContext::textArg, textArg);
    }

    /* ============= */

    record Translation(Integer id, String origin, String translated) implements XmlDto {

        @Override
        public String text() {
            return origin;
        }

    }

}
