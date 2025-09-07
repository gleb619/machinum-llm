package machinum.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for parsing text into properties, supporting whitespace in keys.
 * Unlike standard Java Properties, this parser allows keys to contain spaces.
 */
@Slf4j
public class PropertiesParser {

    // Pattern to match key-value pairs, supporting whitespace in keys
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("^([^=]+?)\\s*=\\s*(.*)$");

    /**
     * Parses the given text into a map of properties.
     * This method supports keys with whitespace and handles line continuations.
     *
     * @param text The text containing properties in key=value format
     * @return A map of the parsed properties
     */
    public static Map<String, String> parseProperties(String text) {
        Map<String, String> properties = new HashMap<>();

        if (text == null || text.isEmpty()) {
            log.warn("No text provided for parsing properties");
            return properties;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            StringBuilder continuedLine = new StringBuilder();
            String pendingKey = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }

                // Handle line continuation
                if (line.endsWith("\\")) {
                    if (pendingKey == null) {
                        // Start of a new continued line
                        Matcher matcher = PROPERTY_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            pendingKey = matcher.group(1).trim();
                            continuedLine.append(matcher.group(2), 0, matcher.group(2).length() - 1);
                        }
                    } else {
                        // Continuation of previous line
                        continuedLine.append(line, 0, line.length() - 1);
                    }
                    continue;
                }

                if (pendingKey != null) {
                    // End of continued line
                    continuedLine.append(line);
                    properties.put(pendingKey, continuedLine.toString());
                    pendingKey = null;
                    continuedLine.setLength(0);
                } else {
                    // Process normal line
                    Matcher matcher = PROPERTY_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String key = matcher.group(1).trim();
                        String value = matcher.group(2);
                        properties.put(key, value);
                    } else {
                        log.warn("Ignoring invalid property line: {}", line);
                    }
                }
            }

            // Handle any pending continued line
            if (pendingKey != null) {
                properties.put(pendingKey, continuedLine.toString());
            }

        } catch (IOException e) {
            log.error("Error parsing properties", e);
        }

        log.info("Parsed {} properties from input text", properties.size());
        return properties;
    }

    /**
     * Converts a map of properties back to a string representation.
     *
     * @param properties The map of properties to convert
     * @return A string representation of the properties
     */
    public static String propertiesToString(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            builder.append(entry.getKey())
                    .append(" = ")
                    .append(entry.getValue())
                    .append("\n");
        }

        return builder.toString();
    }

    /**
     * Gets a property value as a boolean.
     *
     * @param properties   The properties map
     * @param key          The property key
     * @param defaultValue The default value to return if the property doesn't exist or is invalid
     * @return The boolean value of the property, or the default value
     */
    public static boolean getBooleanProperty(Map<String, String> properties, String key, boolean defaultValue) {
        String value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }

        value = value.trim().toLowerCase();
        if (value.equals("true") || value.equals("yes") || value.equals("1")) {
            return true;
        } else if (value.equals("false") || value.equals("no") || value.equals("0")) {
            return false;
        }

        return defaultValue;
    }

    /**
     * Gets a property value as an integer.
     *
     * @param properties   The properties map
     * @param key          The property key
     * @param defaultValue The default value to return if the property doesn't exist or is invalid
     * @return The integer value of the property, or the default value
     */
    public static int getIntProperty(Map<String, String> properties, String key, int defaultValue) {
        String value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer property value for key '{}': {}", key, value);
            return defaultValue;
        }
    }

}
