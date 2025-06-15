package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.processor.core.AssistantContext;
import machinum.util.CodeBlockExtractor;
import machinum.util.TextUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var textArg = flowContext.textArg();
        var list = TextUtil.toParagraphs(textArg.stringValue()).stream()
                .map(s -> new Translation(escapeXml(s), ""))
                .collect(Collectors.toList());
        var newText = convertTranslations(list);

        return super.translate(flowContext.replace(FlowContext::textArg, text(newText)))
                .replace(FlowContext::textArg, textArg);
    }

    @Override
    protected String parseTranslatedText(AssistantContext.Result context) {
        if (context.entity() instanceof List<?> l && l.getFirst() instanceof Translation) {
            List<Translation> list = context.entity();
            return list.stream()
                    .map(Translation::translated)
                    .collect(Collectors.joining("\n\n"));
        }

        return context.result();
    }

    @Override
    protected AssistantContext.Result processAssistantResult(AssistantContext.Result result) {
        var rawText = result.result();
        var rawXml = CodeBlockExtractor.extract(rawText).trim();
        var mapResult = parseTranslations(rawXml);
        if (mapResult.isEmpty()) {
            throw new AppIllegalStateException("All titles are empty");
        }
        result.setEntity(mapResult);

        return result;
    }

    @SneakyThrows
    private List<Translation> parseTranslations(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); // Security hardening

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList resultNodes = doc.getElementsByTagName("result");

            return IntStream.range(0, resultNodes.getLength())
                    .mapToObj(i -> (Element) resultNodes.item(i))
                    .map(element -> new Translation(
                            element.getElementsByTagName("origin").item(0).getTextContent(),
                            element.getElementsByTagName("translated").item(0).getTextContent()
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Got error: `{}`; response:\n{}", e.getMessage(), xml);
            return ExceptionUtils.rethrow(e);
        }
    }

    @SneakyThrows
    public String convertTranslations(List<Translation> translations) {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();

        // Create root element
        Element root = doc.createElement("results");
        doc.appendChild(root);

        // Add each translation
        for (Translation t : translations) {
            Element result = doc.createElement("result");

            Element origin = doc.createElement("origin");
            origin.setTextContent(t.origin());
            result.appendChild(origin);

            Element translated = doc.createElement("translated");
            translated.setTextContent(t.translated());
            result.appendChild(translated);

            root.appendChild(result);
        }

        // Convert to XML string
        Transformer transformer = TransformerFactory.newInstance()
                .newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    record Translation(String origin, String translated) {
    }

}
