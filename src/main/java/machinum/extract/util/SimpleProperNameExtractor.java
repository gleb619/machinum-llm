package machinum.extract.util;

import machinum.util.TextUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for extracting proper names, nouns, and nicknames from text.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SimpleProperNameExtractor {

    private static final Pattern PROPER_NAME_PATTERN = Pattern.compile(
            "\\b(?:[A-Z][a-z]{1,}\\s?){1,}\\b"  // Match capitalized words
    );

    private static final Pattern NICKNAME_PATTERN = Pattern.compile(
            "\"([^\"]+)\"|'([^']+)'|\\s@(\\w+)|\\s\"([^\"]+)\"\\s"  // Match text in quotes or Twitter-style handles
    );

    private static final Set<String> COMMON_WORDS = new HashSet<>(List.of(
            "The", "A", "An", "And", "But", "Or", "For", "Nor", "On", "At", "To", "From",
            "By", "With", "In", "Of", "As", "If", "Then", "Else", "When", "I", "You",
            "He", "She", "It", "We", "They", "Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday", "Saturday", "Sunday", "January", "February", "March", "April", "May",
            "June", "July", "August", "September", "October", "November", "December", "There", "Thus", "Those", "This",
            "Their", "Even", "What", "Where", "Who", "How", "Why", "However", "Do", "Just", "Now", "Before", "Right",
            "Left", "Yet", "Due", "Did", "No", "Yes", "Be", "So"
    ));

    @Getter(lazy = true)
    private static final List<String> stopWords = loadStopWords();

    @SneakyThrows
    private static List<String> loadStopWords() {
        var resource = new ClassPathResource("stopwords/en.txt");
        try (var inputStream = resource.getInputStream()) {
            String string = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(string.split("\n"))
                    .filter(TextUtil::isNotEmpty)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    public static boolean wordAllowed(String word) {
        return !getStopWords().contains(word) && word.length() <= 50;
    }

    /**
     * Extracts proper names from the given text.
     *
     * @param text The text to extract proper names from
     * @return A list of extracted proper names
     */
    public static List<String> extractProperNames(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> properNames = new ArrayList<>();
        Matcher properNameMatcher = PROPER_NAME_PATTERN.matcher(text);

        while (properNameMatcher.find()) {
            String match = properNameMatcher.group().trim();

            // Skip single common words
            if (!getStopWords().contains(match.toLowerCase()) && !isSentenceStart(text, properNameMatcher.start())) {
                properNames.add(match);
            }
        }

        return properNames;
    }

    /**
     * Extracts nicknames from the given text.
     *
     * @param text The text to extract nicknames from
     * @return A list of extracted nicknames
     */
    public static List<String> extractNicknames(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> nicknames = new ArrayList<>();
        Matcher nicknameMatcher = NICKNAME_PATTERN.matcher(text);

        while (nicknameMatcher.find()) {
            for (int i = 1; i <= nicknameMatcher.groupCount(); i++) {
                if (nicknameMatcher.group(i) != null) {
                    // Skip single common words
                    String match = nicknameMatcher.group(i).trim();
                    if (!getStopWords().contains(match.toLowerCase()) && !match.isBlank()) {
                        nicknames.add(match);
                    }

                    break;
                }
            }
        }

        return nicknames;
    }

    /**
     * Extracts all names (proper names and nicknames) from the given text.
     *
     * @param text The text to extract names from
     * @return A list of extracted names
     */
    public static List<String> extractAllNames(String text) {
        var allNames = new HashSet<String>();
        allNames.addAll(extractProperNames(text));
        allNames.addAll(extractNicknames(text));

        return allNames.stream().sorted()
                .collect(Collectors.toList());
    }

    /**
     * Checks if a position in the text is likely the start of a sentence.
     *
     * @param text     The full text
     * @param position The position to check
     * @return true if the position is likely the start of a sentence
     */
    private static boolean isSentenceStart(String text, int position) {
        if (position == 0) {
            return true;
        }

        // Check if previous character is a sentence-ending punctuation or a newline
        char prevChar = text.charAt(position - 1);
        return prevChar == '.' || prevChar == '?' || prevChar == '!' || prevChar == '\n';
    }

}