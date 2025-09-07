package machinum.ssml;

import com.guseyn.broken_xml.Element;
import com.guseyn.broken_xml.ParsedXML;
import com.guseyn.broken_xml.Text;
import com.guseyn.broken_xml.XmlDocument;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SSMLParser2 {

    // Supported SSML tags.
    private static final Set<String> SUPPORTED_TAGS = new HashSet<>(Arrays.asList(
            "speak", "break", "prosody", "p", "s"
    ));

    public static String replaceLast(String text, String regex, String replacement) {
        return text.replaceFirst("(?s)" + regex + "(?!.*?" + regex + ")", replacement);
    }

    /**
     * Validates an SSML string.
     * Returns true if all tags are supported; false otherwise.
     */
    public boolean validate(String ssml) {
        XmlDocument document = new ParsedXML(ssml).document();
        return validateElements(document.roots());
    }

    // Recursively validate elements.
    private boolean validateElements(List<Element> elements) {
        for (Element element : elements) {
            if (!SUPPORTED_TAGS.contains(element.name())) {
                return false;
            }
            if (!validateElements(element.children())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Repairs the SSML string by removing unsupported tags while preserving their inner content.
     * Returns a new SSML string.
     */
    public String repair(String ssml) {
        String rawXml = SSMLParser.XmlTagReplacer.replaceSelfClosingTags(ssml);
        XmlDocument document = new ParsedXML(rawXml).document();
        String result = repairElements(document.roots());
        return SSMLBuilder.format(result);
    }

    // Recursively repair elements: remove unsupported elements but keep their children.
    private String repairElements(List<Element> elements) {
        StringBuilder repairedSSML = new StringBuilder();

        for (Element element : elements) {
            var tagIsOpened = false;
            var name = element.name();

            if (SUPPORTED_TAGS.contains(name)) {
                tagIsOpened = true;
                repairedSSML.append("<").append(name);
                element.attributes().forEach(attr -> {
                            var attrName = attr.name();
                            var attrValue = replaceLast(attr.value(), "\"", "");
                            repairedSSML.append(" ").append(attrName).append("=\"")
                                    .append(attrValue).append("\"");
                        }
                );
                repairedSSML.append(">");
                if (!element.children().isEmpty()) {
                    repairedSSML.append(repairElements(element.children()));
                } else {
                    repairedSSML.append(element.texts().stream()
                            .map(Text::value)
                            .collect(Collectors.joining("\n"))
                    );
                }
                repairedSSML.append("</").append(name).append(">");
            } else {
                // Append the inner content of unsupported elements.
                repairedSSML.append(element.texts().stream()
                        .map(Text::value)
                        .collect(Collectors.joining()));
                repairedSSML.append(repairElements(element.children()));
            }
        }

        return repairedSSML.toString();
    }

}
