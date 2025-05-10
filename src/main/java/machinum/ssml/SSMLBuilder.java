package machinum.ssml;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

public class SSMLBuilder {

    private final StringBuilder builder;

    public SSMLBuilder() {
        builder = new StringBuilder();
        builder.append("<speak>");
    }

    @SneakyThrows
    public static String format(String input) {
        Source xmlInput = new StreamSource(new StringReader(input));
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute("indent-number", 2);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");

        StreamResult result = new StreamResult(new StringWriter());
        transformer.transform(xmlInput, result);

        return result.getWriter().toString();
    }

    public SSMLBuilder appendText(String text) {
        builder.append(text);
        return this;
    }

    public SSMLBuilder paragraph(String content) {
        builder.append("<p>").append(content).append("</p>");
        return this;
    }

    public SSMLBuilder sentence(String content) {
        builder.append("<s>").append(content).append("</s>");
        return this;
    }

    public SSMLBuilder rate(String content, Rate rate) {
        return prosody(content, rate.getValue(), "");
    }

    public SSMLBuilder pitch(String content, Pitch pitch) {
        return prosody(content, "", pitch.getValue());
    }

    public SSMLBuilder prosody(String content, String rate, String pitch) {
        builder.append("<prosody");
        if (rate != null && !rate.isEmpty()) {
            builder.append(" rate=\"").append(rate).append("\"");
        }
        if (pitch != null && !pitch.isEmpty()) {
            builder.append(" pitch=\"").append(pitch).append("\"");
        }
        builder.append(">");
        builder.append(content);
        builder.append("</prosody>");
        return this;
    }

    public SSMLBuilder pause(String time, String strength) {
        builder.append("<break");
        if (time != null && !time.isEmpty()) {
            builder.append(" time=\"").append(time).append("\"");
        }
        if (strength != null && !strength.isEmpty()) {
            builder.append(" strength=\"").append(strength).append("\"");
        }
        builder.append("/>");
        return this;
    }

    public String build() {
        builder.append("</speak>");
        return format(builder.toString());
    }

    public void reset() {
        builder.setLength(0);
        builder.append("<speak>");
    }

    @Getter
    @RequiredArgsConstructor
    public enum Rate {

        X_SLOW("x-slow"),
        SLOW("slow"),
        MEDIUM("medium"),
        FAST("fast"),
        X_FAST("x-fast"),
        NONE("");

        final String value;

    }

    @Getter
    @RequiredArgsConstructor
    public enum Pitch {

        X_LOW("x-low"),
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        X_HIGH("x-high"),
        NONE("");

        final String value;

    }

}

