package machinum.ssml;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static machinum.ssml.SSMLBuilder.format;

@Slf4j
public class SSMLParser {

    // Supported SSML tags.
    private static final Set<String> SUPPORTED_TAGS = new HashSet<>(Arrays.asList(
            "speak", "break", "prosody", "p", "s"
    ));

    private static String fixTagNames(String ssml) {
        return ssml.replaceFirst("^xml", "")
                .replaceAll("prosодие", "prosody")
                .replaceAll("prosodie", "prosody")
                .replaceAll("proсодие", "prosody")
                .replaceAll("prosodyе", "prosody")
                .replaceAll("proсody", "prosody")
                ;
    }

    /**
     * Validates an SSML string.
     * Returns true if all tags are supported; false otherwise.
     * Throws Exception if the SSML is not well-formed XML.
     */
    @SneakyThrows
    public boolean validate(String ssml) {
        try {
            Document document = parseXML(ssml);
            return validateNode(document.getDocumentElement());
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("ERROR: ", e);
            } else {
                log.error("ERROR: {}", e.getMessage());
            }

            return false;
        }
    }

    // Recursively validate nodes.
    private boolean validateNode(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String tagName = node.getNodeName();
            if (!SUPPORTED_TAGS.contains(tagName)) {
                // Unsupported tag found.
                return false;
            }
        }
        // Check children.
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!validateNode(children.item(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Repairs the SSML string by removing unsupported tags while preserving their inner text/content.
     * Returns a new SSML string.
     * Throws Exception if the SSML is not well-formed XML.
     */
    @SneakyThrows
    public String repair(String ssml) {
        try {
            var xmlWithTags = fixTagNames(ssml);
            var document = parseXML(xmlWithTags);

            repairNode(document.getDocumentElement());

            var result = serialize(document)
                    .replaceAll("\n\\s+", "\n")
                    .replaceAll("\n", " ");

            return format(result)
                    .replaceAll("(?m)^[ \\t]*\\r?\\n", "");
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("ERROR: ", e);
            } else {
                log.error("ERROR: {}", e.getMessage());
            }

            return ssml;
        }
    }

    public String rawText(String xmlInput) {
        return Jsoup.parse(xmlInput, "", org.jsoup.parser.Parser.xmlParser())
                .text();
    }

    // Recursively repair nodes: remove unsupported element nodes but keep their children.
    private void repairNode(Node node) {
        NodeList children = node.getChildNodes();
        // We need to iterate over a static list because we may modify the children list.
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = child.getNodeName();
                if (!SUPPORTED_TAGS.contains(tagName)) {
                    // Move all children of the unsupported node into its parent.
                    while (child.hasChildNodes()) {
                        Node grandChild = child.getFirstChild();
                        child.removeChild(grandChild);
                        node.insertBefore(grandChild, child);
                    }
                    // Remove the unsupported node.
                    node.removeChild(child);
                } else {
                    // Recursively process supported child node.
                    repairNode(child);
                }
            }
        }
    }

    // Helper method to parse an XML string into a Document.
    private Document parseXML(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entity processing for security.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    // Helper method to serialize a Document back to string.
    private String serialize(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        return writer.toString();
    }

    public static class XmlTagReplacer {

        /**
         * Replaces all self-closing tags in the XML input with empty tags.
         *
         * @return The modified XML string with self-closing tags replaced by empty tags.
         */
        public static String replaceSelfClosingTags(String xmlInput) {
            try {
                // Parse the XML input using JSoup (relaxed parser)
                var document = Jsoup.parse(xmlInput, "", org.jsoup.parser.Parser.xmlParser());

                // Traverse the DOM tree and replace self-closing tags
                processNode(document);

                // Serialize the modified DOM back to a string
                return document.outerHtml();
            } catch (Exception e) {
                throw new RuntimeException("Error processing XML: " + e.getMessage(), e);
            }
        }

        /**
         * Recursively processes each node in the DOM tree.
         * If a node is an element with no child nodes, it is converted to an empty tag.
         *
         * @param node The current node being processed.
         */
        private static void processNode(org.jsoup.nodes.Node node) {
            if (node instanceof org.jsoup.nodes.Element element) {

                // If the element has no child nodes, convert it to an empty tag
                if (!element.hasText() && element.childNodes().isEmpty()) {
                    element.appendChild(new org.jsoup.nodes.TextNode(""));
                }
            }

            // Recursively process child nodes
            for (var child : node.childNodes()) {
                processNode(child);
            }
        }

    }

}
