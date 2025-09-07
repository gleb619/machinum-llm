package machinum.util;

import org.junit.jupiter.api.Test;

import static machinum.util.CodeBlockExtractor.*;

class CodeBlockExtractorTest {

    @Test
    public void mainTest() {
        // Example text with code blocks
        String textWithCodeBlock = """
                Here is some sample code:
                ```java
                public class HelloWorld {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                ```
                This is how you say hello in Java.
                """;

        // Example text without code blocks
        String textWithoutCodeBlock = "This is just plain text with no code blocks.";

        // Extract code from text with code block
        String extractedCode = extractCode(textWithCodeBlock);
        System.out.println("Extracted code:");
        System.out.println(extractedCode);

        // Try to extract code from text without code block
        String result = extractCode(textWithoutCodeBlock);
        System.out.println("\nResult from text without code block:");
        System.out.println(result);

        // Example with multiple code blocks
        String textWithMultipleCodeBlocks = "First code block:\n```java\nString message = \"Hello\";\n```\nSecond code block:\n```java\nSystem.out.println(message);\n```";

        String allExtractedCode = extractAllCode(textWithMultipleCodeBlocks);
        System.out.println("\nAll extracted code blocks:");
        System.out.println(allExtractedCode);

        int blockCount = countCodeBlocks(textWithMultipleCodeBlocks);
        System.out.println("\nNumber of code blocks: " + blockCount);
    }

}
