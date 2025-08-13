package machinum.processor.core;

import lombok.SneakyThrows;
import machinum.util.TextUtil;
import org.apache.commons.lang3.math.NumberUtils;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public interface XmlSupport {

    Pattern XML_HEADER_PATTERN = Pattern.compile("^<\\?xml[^>]+\\?>");

    Pattern WRONG_XML_TAG_PATTERN = Pattern.compile("<([^>]+)>");

    default String fixXmlTags(String text) {
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

    default String fixXmlValues(String input) {
        if (input == null) return null;

        // Remove invalid numeric character references like &#1;, &#2;, etc.
        Pattern invalidNumericReferences = Pattern.compile("&#(?:[0-8]|1[0-5]|1[7-9]|2[0-6]|2[8-9]|3[0-1]);");
        Matcher matcher = invalidNumericReferences.matcher(input);
        input = matcher.replaceAll("");

        // Regular expression to match invalid XML characters
        Pattern invalidXMLChars = Pattern.compile(
                "[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\x{10000}-\\x{10FFFF}]",
                Pattern.UNICODE_CHARACTER_CLASS
        );

        matcher = invalidXMLChars.matcher(input);
        return matcher.replaceAll("");
    }

    default String parseStringValue(NodeList nodeList) {
        if (nodeList.getLength() > 0) {
            var item = nodeList.item(0);
            return TextUtil.firstNotEmpty(item.getTextContent(), "");
        }

        return "";
    }

    @SneakyThrows
    default <T extends XmlDto> String convertToXml(List<T> items, String fieldName, Function<T, String> extractor) {
        var doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();

        // Create root element
        var root = doc.createElement("results");
        doc.appendChild(root);

        // Add each items
        for (var t : items) {
            var result = doc.createElement("result");

            var id = doc.createElement("id");
            id.setTextContent(String.valueOf(t.id()));
            result.appendChild(id);

            if (Objects.nonNull(t.text()) && !t.text().isBlank()) {
                var origin = doc.createElement("text");
                origin.setTextContent(t.text());
                result.appendChild(origin);
            }

            var resultText = extractor.apply(t);
            if (Objects.nonNull(resultText) && !resultText.isBlank()) {
                var translated = doc.createElement(fieldName);
                translated.setTextContent(resultText);
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

    @SneakyThrows
    default <T extends XmlDto> List<T> parseTextFromXml(String xml, String fieldName, BiFunction<Integer, String, T> mapper) {
        var localXml = fixXmlValues(fixXmlTags(xml));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); // Security hardening

        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(localXml)));

        var resultNodes = doc.getElementsByTagName("result");

        return IntStream.range(0, resultNodes.getLength())
                .mapToObj(i -> (Element) resultNodes.item(i))
                .map(element -> {
                    var id = element.getElementsByTagName("id");
                    var origin = element.getElementsByTagName(fieldName);

                    return mapper.apply(NumberUtils.toInt(parseStringValue(id), 0), parseStringValue(origin));
                })
                .toList();
    }

    interface XmlDto {

        Integer id();

        String text();

    }

}
