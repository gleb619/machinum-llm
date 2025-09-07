package machinum.processor.core;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public interface MarkdownSupport {

    int DEFAULT_LINE_LENGTH = 80;
    String NEWLINE = System.lineSeparator();
    Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s.*$", Pattern.MULTILINE);
    Pattern LIST_PATTERN = Pattern.compile("^[\\s]*[-*+]\\s.*$", Pattern.MULTILINE);

    private static String formatParagraph(String paragraph, int maxLineLength) {
        String[] words = paragraph.split("\\s+");
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 <= maxLineLength) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                result.append(currentLine).append(NEWLINE);
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            result.append(currentLine);
        }

        return result.toString();
    }

    private static String formatHeader(String header) {
        // Ensure single space after # symbols
        return header.replaceAll("^(#{1,6})\\s*", "$1 ");
    }

    private static String formatList(String list, int maxLineLength) {
        String[] lines = list.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
//            if(line.isBlank()) {
//                result.append(line).append(NEWLINE);
//            }
            // Calculate indentation level
            String trimLine = line.trim();
            int indent = line.indexOf(trimLine);
            String listMarker; // Get "- " or "* " or "+ "
            String content;

            if (trimLine.length() > 1) {
                listMarker = trimLine.substring(0, 2);
                content = trimLine.substring(2);
            } else {
                listMarker = trimLine;
                content = "";
            }

            // Format the content with proper indentation
            String formattedContent = formatParagraph(content, maxLineLength - indent - 2);
            String indentation = " ".repeat(indent);

            // Apply indentation to each line
            String[] contentLines = formattedContent.split("\n");
            for (int i = 0; i < contentLines.length; i++) {
                result.append(indentation);
                if (i == 0) {
                    result.append(listMarker);
                } else {
                    result.append("  ");
                }
                result.append(contentLines[i]).append(NEWLINE);
            }
        }

        return result.toString();
    }

    /**
     * Formats markdown text according to readability rules.
     *
     * @param markdown The input markdown text
     * @return Formatted markdown text
     */
    default String formatMarkdown(@NonNull String markdown) {
        return formatMarkdown(markdown, DEFAULT_LINE_LENGTH);
    }

    /**
     * Formats markdown text according to readability rules with custom line length.
     *
     * @param markdown      The input markdown text
     * @param maxLineLength Maximum line length (must be between 40 and 120)
     * @return Formatted markdown text
     */
    default String formatMarkdown(@NonNull String markdown, int maxLineLength) {
        if (maxLineLength < 40 || maxLineLength > 120) {
            throw new IllegalArgumentException("Line length must be between 40 and 120 characters");
        }

        StringBuilder result = new StringBuilder();
        String[] blocks = markdown.split("\n\\s*\n");

        for (int i = 0; i < blocks.length; i++) {
            String block = blocks[i].trim();

            // Skip empty blocks
            if (block.isEmpty()) {
                continue;
            }

            // Preserve code blocks
            if (block.startsWith("```")) {
                result.append(block).append(NEWLINE).append(NEWLINE);
                continue;
            }

            // Format headers
            if (HEADER_PATTERN.matcher(block).matches()) {
                result.append(formatHeader(block)).append(NEWLINE).append(NEWLINE);
                continue;
            }

            // Format lists
            if (LIST_PATTERN.matcher(block).matches()) {
                result.append(formatList(block, maxLineLength)).append(NEWLINE);
                continue;
            }

            // Format regular paragraphs
            result.append(formatParagraph(block, maxLineLength)).append(NEWLINE);

            // Add extra newline between blocks
            if (i < blocks.length - 1) {
                result.append(NEWLINE);
            }
        }

        return result.toString().trim();
    }

    /**
     * Validates if markdown text follows the formatting rules.
     *
     * @param markdown The markdown text to validate
     * @return List of formatting issues found, empty list if no issues
     */
    default List<String> validateMarkdown(@NonNull String markdown) {
        List<String> issues = new ArrayList<>();
        String[] lines = markdown.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check line length (ignore code blocks and headers)
            if (!line.trim().startsWith("#") && !line.trim().startsWith("```") &&
                    line.length() > DEFAULT_LINE_LENGTH) {
                issues.add(String.format("Line %d exceeds %d characters", i + 1, DEFAULT_LINE_LENGTH));
            }

            // Check header formatting
            if (line.trim().startsWith("#")) {
                if (!line.matches("^#{1,6}\\s.*$")) {
                    issues.add(String.format("Header at line %d should have single space after #", i + 1));
                }
            }

            // Check blank lines between sections
            if (i > 0 && i < lines.length - 1) {
                if (line.trim().startsWith("#") && !lines[i - 1].trim().isEmpty()) {
                    issues.add(String.format("Missing blank line before header at line %d", i + 1));
                }
            }
        }

        return issues;
    }

}
