package machinum.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting content between code block markers (```) from text.
 */
@Slf4j
public class CodeBlockExtractor {

    // Pattern to match content between ``` markers
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:.*?\\n)?(.*?)```", Pattern.DOTALL);

    /**
     * Extracts content between code block markers (```) from the given text.
     * If no code blocks are found, returns the original text.
     *
     * @param text The input text that may contain code blocks
     * @return The content between code block markers, or the original text if no code blocks found
     */
    public static String extractCode(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            String codeContent = matcher.group(1);
            log.debug("Code block found and extracted");
            return codeContent.trim();
        } else {
            log.debug("No code block found, returning original text");
            return text;
        }
    }

    /**
     * Extracts all code blocks from the given text and returns them as a single string,
     * with blocks separated by newlines.
     *
     * @param text The input text that may contain multiple code blocks
     * @return A string containing all code blocks concatenated with newlines,
     * or the original text if no code blocks found
     */
    public static String extractAllCode(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        StringBuilder allCode = new StringBuilder();
        boolean foundAny = false;

        while (matcher.find()) {
            if (foundAny) {
                allCode.append("\n\n");
            }
            allCode.append(matcher.group(1).trim());
            foundAny = true;
        }

        if (foundAny) {
            log.debug("Found and extracted {} code blocks", countMatches(matcher));
            return allCode.toString();
        } else {
            log.debug("No code blocks found, returning original text");
            return text;
        }
    }

    /**
     * Counts the number of code blocks in the given text.
     *
     * @param text The input text that may contain code blocks
     * @return The number of code blocks found
     */
    public static int countCodeBlocks(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        return countMatches(matcher);
    }

    /**
     * Helper method to count matches from a matcher.
     * Note: This resets the matcher's state.
     */
    private static int countMatches(Matcher matcher) {
        matcher.reset();
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Checks if the text contains any code blocks.
     *
     * @param text The input text to check
     * @return true if at least one code block is found, false otherwise
     */
    public static boolean containsCodeBlock(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        return matcher.find();
    }

}
