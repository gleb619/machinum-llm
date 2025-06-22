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
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static machinum.flow.FlowContextActions.text;
import static org.apache.commons.lang3.StringEscapeUtils.escapeXml;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslaterXmlBody extends AbstractTranslaterBody {

    public static final Pattern XML_HEADER_PATTERN = Pattern.compile("^<\\?xml[^>]+\\?>");
    public static final Pattern WRONG_XML_TAG_PATTERN = Pattern.compile("<([^>]+)>");

    @Getter
    @Value("classpath:prompts/custom/system/TranslateBodySystem.xml.ST")
    private final Resource systemTemplate;
    @Getter
    @Value("classpath:prompts/custom/TranslateBody.xml.ST")
    private final Resource translateTemplate;

    public static String fixXmlTags(String text) {
        var xmlDeclarationMatcher = XML_HEADER_PATTERN.matcher(text);

        // Extract and preserve the XML declaration if it exists
        var xmlDeclaration = "";
        if (xmlDeclarationMatcher.find()) {
            xmlDeclaration = xmlDeclarationMatcher.group();
            text = text.substring(xmlDeclaration.length()).trim();
        }

        // Process the rest of the XML content
        var tagMatcher = WRONG_XML_TAG_PATTERN.matcher(text);
        var result = new StringBuilder();

        while (tagMatcher.find()) {
            String tagContent = tagMatcher.group(1);
            String cleanedTag = tagContent.replaceAll("\\s+", "");
            tagMatcher.appendReplacement(result, "<" + cleanedTag + ">");
        }
        tagMatcher.appendTail(result);

        return xmlDeclaration + result;
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

    @Override
    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var textArg = flowContext.textArg();
        var counter = new AtomicInteger(1);
        var list = TextUtil.toParagraphs(textArg.stringValue()).stream()
                .map(s -> new Translation(counter.getAndIncrement(), escapeXml(s), ""))
                .collect(Collectors.toList());
        var newText = convertTranslations(list);

        return super.translate(flowContext.replace(FlowContext::textArg, text(newText)))
                .replace(FlowContext::textArg, textArg);
    }

    @SneakyThrows
    private List<Translation> parseTranslations(String xml) {
        var localXml = fixXmlTags(xml);
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); // Security hardening

            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new InputSource(new StringReader(localXml)));

            var resultNodes = doc.getElementsByTagName("result");

            return IntStream.range(0, resultNodes.getLength())
                    .mapToObj(i -> (Element) resultNodes.item(i))
                    .map(element -> {
                        var id = element.getElementsByTagName("id");
                        var origin = element.getElementsByTagName("origin");
                        var translated = element.getElementsByTagName("translated");

                        return new Translation(
                                NumberUtils.toInt(parseValue(id), 0),
                                parseValue(origin),
                                parseValue(translated)
                        );
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Got error: `{}`; response:\n{}", e.getMessage(), xml);
            return ExceptionUtils.rethrow(e);
        }
    }

    @SneakyThrows
    public String convertTranslations(List<Translation> translations) {
        var doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();

        // Create root element
        var root = doc.createElement("results");
        doc.appendChild(root);

        // Add each translation
        for (var t : translations) {
            var result = doc.createElement("result");

            var id = doc.createElement("id");
            id.setTextContent(String.valueOf(t.id()));
            result.appendChild(id);

            if (Objects.nonNull(t.origin()) && !t.origin().isBlank()) {
                var origin = doc.createElement("origin");
                origin.setTextContent(t.origin());
                result.appendChild(origin);
            }

            if (Objects.nonNull(t.translated()) && !t.translated().isBlank()) {
                var translated = doc.createElement("translated");
                translated.setTextContent(t.translated());
                result.appendChild(translated);
            }

            root.appendChild(result);
        }

        // Convert to XML string
        var transformer = TransformerFactory.newInstance()
                .newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        var writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private String parseValue(NodeList nodeList) {
        if (nodeList.getLength() > 0) {
            var item = nodeList.item(0);
            return TextUtil.firstNotEmpty(item.getTextContent(), "");
        }

        return "";
    }

    record Translation(Integer id, String origin, String translated) {
    }

}
