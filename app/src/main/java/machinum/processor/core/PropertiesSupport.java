package machinum.processor.core;

import machinum.util.PropertiesParser;

import java.util.Map;

public interface PropertiesSupport {

    default Map<String, String> parsePropertiesFromMarkdown(String text) {
        return PropertiesParser.parseProperties(text);
    }

}
