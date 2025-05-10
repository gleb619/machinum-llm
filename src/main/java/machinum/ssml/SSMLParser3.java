package machinum.ssml;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SSMLParser3 {

    private static final Map<String, List<String>> VALID_TAGS = new HashMap<>();
    private static final Pattern TAG_PATTERN = Pattern.compile("<(/?)([\\w-]+)([^>]*)>", Pattern.DOTALL);

    static {
        // Initialize valid tags with their attributes
        VALID_TAGS.put("speak", new ArrayList<>());

        List<String> breakAttrs = new ArrayList<>();
        breakAttrs.add("time");
        breakAttrs.add("strength");
        VALID_TAGS.put("break", breakAttrs);

        List<String> prosodyAttrs = new ArrayList<>();
        prosodyAttrs.add("rate");
        prosodyAttrs.add("pitch");
        VALID_TAGS.put("prosody", prosodyAttrs);

        VALID_TAGS.put("p", new ArrayList<>());
        VALID_TAGS.put("s", new ArrayList<>());
    }

    @Getter
    private final List<String> errors = new ArrayList<>();

    /**
     * Main method to demonstrate usage
     */
    public static void main(String[] args) {
        String invalidSSML = "<speak>\n" +
                "  <p>Первый параграф.</p>\n" +
                "  <prosody rate=\"x-fast\">Второй параграф.</prosody>\n" +
                "   Некоторый текстs\n" +
                "  <s>Первое предложение.</s>\n" +
                "  <s>Второе предложение.</s>\n" +
                "  <prosoдy rate=\"x-slow\">Я говорю довольно медленно</prosodи>\n" +
                "  <break time=\"2000ms\"/>\n" +
                "</speak>";

        SSMLParser3 parser = new SSMLParser3();
        String normalizedSSML = parser.repair(invalidSSML);

        System.out.println("Normalized SSML:");
        System.out.println(normalizedSSML);
        System.out.println("\nErrors and corrections:");
        for (String error : parser.getErrors()) {
            System.out.println("- " + error);
        }
    }

    /**
     * Parses and normalizes SSML text
     *
     * @param ssmlText the input SSML text which may contain errors
     * @return normalized SSML text
     */
    public String repair(String ssmlText) {
        errors.clear();

        // First, try to correct common errors
        String preprocessed = preprocessSSML(ssmlText);

        try {
            // Try to parse with XML parser
            Document doc = parseXML(preprocessed);
            validateAndFixNodes(doc.getDocumentElement());
            return documentToString(doc);
        } catch (Exception e) {
            // If parsing fails, use regex-based approach
            errors.add("XML parsing failed: " + e.getMessage());
            return manualTagFix(preprocessed);
        }
    }

    /**
     * Manual preprocessing of common SSML errors
     */
    private String preprocessSSML(String ssmlText) {
        // Ensure there's a root speak tag
        if (!ssmlText.trim().startsWith("<speak")) {
            ssmlText = "<speak>" + ssmlText + "</speak>";
        }

        // Fix common Cyrillic characters in tag names (like проsoдy -> prosody)
        ssmlText = ssmlText.replaceAll("<проs", "<pros")
                .replaceAll("проs>", "pros>")
                .replaceAll("<прос", "<pros")
                .replaceAll("прос>", "pros>")
                .replaceAll("<проsо", "<proso")
                .replaceAll("проsо>", "proso>")
                .replaceAll("<просо", "<proso")
                .replaceAll("просо>", "proso>")
                .replaceAll("одy", "ody")
                .replaceAll("оди", "ody");

        // Fix common mismatches using regex approach
        StringBuilder result = new StringBuilder();
        List<String> tagStack = new ArrayList<>();

        Matcher matcher = TAG_PATTERN.matcher(ssmlText);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before this tag
            result.append(ssmlText, lastEnd, matcher.start());

            String slash = matcher.group(1);  // "/" or ""
            String tagName = matcher.group(2).toLowerCase();  // tag name
            String attributes = matcher.group(3);  // attributes

            // Check if this is a valid SSML tag
            if (VALID_TAGS.containsKey(tagName)) {
                if (slash.isEmpty()) {
                    // Opening tag
                    tagStack.add(tagName);
                    result.append("<").append(tagName).append(attributes).append(">");
                } else {
                    // Closing tag
                    if (!tagStack.isEmpty()) {
                        String lastTag = tagStack.remove(tagStack.size() - 1);
                        if (!lastTag.equals(tagName)) {
                            errors.add("Mismatched tags: <" + lastTag + "> closed with </" + tagName + ">");
                            // Close with the correct tag
                            result.append("</").append(lastTag).append(">");
                        } else {
                            result.append("</").append(tagName).append(">");
                        }
                    } else {
                        errors.add("Closing tag without opening: </" + tagName + ">");
                        // Skip this closing tag
                    }
                }
            } else {
                errors.add("Unrecognized SSML tag: " + tagName);
                // Skip this tag
            }

            lastEnd = matcher.end();
        }

        // Append remaining text
        result.append(ssmlText.substring(lastEnd));

        // Close any unclosed tags
        for (int i = tagStack.size() - 1; i >= 0; i--) {
            String unclosedTag = tagStack.get(i);
            errors.add("Unclosed tag: <" + unclosedTag + ">");
            result.append("</").append(unclosedTag).append(">");
        }

        return result.toString();
    }

    /**
     * Parse XML using DOM parser
     */
    private Document parseXML(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Validate and fix nodes recursively
     */
    private void validateAndFixNodes(Element element) {
        String tagName = element.getTagName().toLowerCase();

        // Check if this is a valid SSML tag
        if (!VALID_TAGS.containsKey(tagName)) {
            errors.add("Invalid SSML tag: " + tagName);
            // Replace with the nearest valid tag or remove
            // For simplicity, we'll just keep it in this implementation
        }

        // Validate attributes
        if (VALID_TAGS.containsKey(tagName)) {
            List<String> validAttrs = VALID_TAGS.get(tagName);
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                String attrName = element.getAttributes().item(i).getNodeName();
                if (!validAttrs.contains(attrName)) {
                    errors.add("Invalid attribute '" + attrName + "' for tag <" + tagName + ">");
                    // For a complete implementation, we would remove invalid attributes here
                }
            }
        }

        // Process child nodes recursively
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                validateAndFixNodes((Element) child);
            }
        }
    }

    /**
     * Convert DOM Document to String
     */
    private String documentToString(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            errors.add("Error converting Document to String: " + e.getMessage());
            return null;
        }
    }

    /**
     * Manual tag fixing approach (backup if XML parsing fails)
     */
    private String manualTagFix(String ssmlText) {
        StringBuilder result = new StringBuilder();
        List<String> tagStack = new ArrayList<>();

        Matcher matcher = TAG_PATTERN.matcher(ssmlText);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before this tag
            result.append(ssmlText, lastEnd, matcher.start());

            String slash = matcher.group(1);  // "/" or ""
            String tagName = matcher.group(2).toLowerCase();  // normalize tag name
            String attributes = matcher.group(3);  // attributes

            // Check if this is a valid SSML tag
            if (VALID_TAGS.containsKey(tagName)) {
                if (slash.isEmpty()) {
                    // Opening tag
                    tagStack.add(tagName);
                    result.append("<").append(tagName).append(attributes).append(">");
                } else {
                    // Closing tag
                    if (!tagStack.isEmpty()) {
                        String lastTag = tagStack.remove(tagStack.size() - 1);
                        if (!lastTag.equals(tagName)) {
                            errors.add("Mismatched tags: <" + lastTag + "> closed with </" + tagName + ">");
                            // Close with the correct tag
                            result.append("</").append(lastTag).append(">");
                        } else {
                            result.append("</").append(tagName).append(">");
                        }
                    } else {
                        errors.add("Closing tag without opening: </" + tagName + ">");
                        // Skip this closing tag
                    }
                }
            } else {
                // Try to guess the closest valid tag
                String correctedTag = findClosestValidTag(tagName);
                if (correctedTag != null) {
                    errors.add("Corrected tag: " + tagName + " -> " + correctedTag);
                    if (slash.isEmpty()) {
                        // Opening tag
                        tagStack.add(correctedTag);
                        result.append("<").append(correctedTag).append(attributes).append(">");
                    } else {
                        // Closing tag
                        if (!tagStack.isEmpty()) {
                            String lastTag = tagStack.remove(tagStack.size() - 1);
                            result.append("</").append(lastTag).append(">");
                        }
                    }
                } else {
                    errors.add("Could not correct invalid tag: " + tagName);
                    // Skip this tag
                }
            }

            lastEnd = matcher.end();
        }

        // Append remaining text
        result.append(ssmlText.substring(lastEnd));

        // Close any unclosed tags
        for (int i = tagStack.size() - 1; i >= 0; i--) {
            String unclosedTag = tagStack.get(i);
            errors.add("Unclosed tag: <" + unclosedTag + ">");
            result.append("</").append(unclosedTag).append(">");
        }

        return result.toString();
    }

    /**
     * Find the closest valid tag based on Levenshtein distance
     */
    private String findClosestValidTag(String invalidTag) {
        String closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS.keySet()) {
            int distance = levenshteinDistance(invalidTag, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closest = validTag;
            }
        }

        // Only consider it a match if reasonably close
        if (minDistance <= 2) {
            return closest;
        }
        return null;
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    @Data
    @NoArgsConstructor
    public static class SSMLParserResult {
        private String normalizedSSML;
        private List<String> errors;
    }

}
