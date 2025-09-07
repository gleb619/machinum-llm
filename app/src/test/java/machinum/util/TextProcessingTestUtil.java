package machinum.util;

import org.assertj.core.api.Assertions;

import java.util.Arrays;
import java.util.List;

public class TextProcessingTestUtil {

    private static final TextProcessingTestUtil INSTANCE = new TextProcessingTestUtil();

    private TextProcessingTestUtil() {
    }

    public static TextProcessingTestUtil assertLineCount(String text, int expectedLineCount) {
        List<String> lines = text.lines().toList();
        Assertions.assertThat(lines)
                .hasSizeGreaterThanOrEqualTo(expectedLineCount)
                .withFailMessage("Expected %d lines but found %d", expectedLineCount, lines.size());

        return INSTANCE;
    }

    public static TextProcessingTestUtil assertParagraphCount(String text, int expectedParagraphCount) {
        List<String> paragraphs = Arrays.asList(text.split("\\n\\s*\\n"));
        Assertions.assertThat(paragraphs)
                .hasSize(expectedParagraphCount)
                .withFailMessage("Expected %d paragraphs but found %d", expectedParagraphCount, paragraphs.size());

        return INSTANCE;
    }

    public static TextProcessingTestUtil assertWordCount(String text, int expectedWordCount) {
        long wordCount = Arrays.stream(text.split("\\s+")).filter(word -> !word.isEmpty()).count();
        Assertions.assertThat(wordCount)
                .isEqualTo(expectedWordCount)
                .withFailMessage("Expected %d words but found %d", expectedWordCount, wordCount);

        return INSTANCE;
    }

    public static TextProcessingTestUtil assertCharacterCount(String text, int expectedCharacterCount) {
        long characterCount = text.chars().count();
        Assertions.assertThat(characterCount)
                .isGreaterThanOrEqualTo(expectedCharacterCount)
                .withFailMessage("Expected %d characters but found %d", expectedCharacterCount, characterCount);

        return INSTANCE;
    }

    public static TextProcessingTestUtil assertSimilarity(String text1, String text2, double expectedSimilarity) {
        double similarity = calculateJaccardSimilarity(text1, text2);
        Assertions.assertThat(similarity)
                .isCloseTo(expectedSimilarity, Assertions.offset(0.1))
                .withFailMessage("Expected similarity to be close to %.2f but found %.2f", expectedSimilarity, similarity);

        return INSTANCE;
    }

    public static TextProcessingTestUtil assertContainsWord(String text, String word) {
        Assertions.assertThat(text)
                .contains(word)
                .withFailMessage("Expected text to contain word: '%s'", word);

        return INSTANCE;
    }

    public static TextProcessingTestUtil assertDoesNotContainWord(String text, String word) {
        Assertions.assertThat(text)
                .doesNotContain(word)
                .withFailMessage("Expected text to not contain word: '%s'", word);

        return INSTANCE;
    }

    /* ============= */


    private static double calculateJaccardSimilarity(String text1, String text2) {
        List<String> words1 = Arrays.asList(text1.split("\\s+"));
        List<String> words2 = Arrays.asList(text2.split("\\s+"));
        long intersection = words1.stream().filter(words2::contains).count();
        long union = words1.size() + words2.size() - intersection;
        return (double) intersection / union;
    }

}
