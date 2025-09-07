package machinum.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static machinum.util.PropertiesParser.*;

class PropertiesParserTest {

    @Test
    public void testMain() {
        // Example properties text with whitespace in keys
        String propertiesText =
                """
                        # Sample properties file
                        database url = jdbc:mysql://localhost:3306/mydb
                        user name = admin
                        password = secret123
                        max connections = 10
                        enable logging = true
                        retry on failure = yes
                        multiline property = This is a long value \\
                        that spans multiple \\
                        lines in the file
                        """;

        // Parse the properties
        Map<String, String> properties = parseProperties(propertiesText);

        // Print the parsed properties
        System.out.println("Parsed Properties:");
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            System.out.println("Key: [" + entry.getKey() + "], Value: [" + entry.getValue() + "]");
        }

        // Example of getting typed properties
        boolean enableLogging = getBooleanProperty(properties, "enable logging", false);
        boolean retryOnFailure = getBooleanProperty(properties, "retry on failure", false);
        int maxConnections = getIntProperty(properties, "max connections", 5);

        System.out.println("\nTyped Properties:");
        System.out.println("Enable Logging: " + enableLogging);
        System.out.println("Retry on Failure: " + retryOnFailure);
        System.out.println("Max Connections: " + maxConnections);

        // Convert back to string
        String propertyString = propertiesToString(properties);
        System.out.println("\nProperties as String:");
        System.out.println(propertyString);
    }

}
