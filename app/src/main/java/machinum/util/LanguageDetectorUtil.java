package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Patterns for common Russian words and Cyrillic characters
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LanguageDetectorUtil {

    // Common Russian words
    public static final Pattern COMMON_WORDS_PATTERN = Pattern.compile("\\b(и|в|не|на|я|что|тот|быть|с|он|а|по|это|она|этот|к|но|они|мы|как|из|у|который|то|за|свой|весь|год|от|так|о|для|ты|же|все|тут|вот|кто|да|говорить|один|только|или|уже|бы|себя|такой)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Common Russian endings
    public static final Pattern RUSSIAN_ENDINGS_PATTERN = Pattern.compile("(ться|тся|ать|ить|еть|уть|ешь|ишь|ует|ают|ите|ете|ают|яют|ыть|ешь|ете|ого|ему|ыми|ыми|ами|ями)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Cyrillic character pattern (covers Russian alphabet)
    public static final Pattern CYRILLIC_PATTERN = Pattern.compile("[а-яА-ЯёЁ]+");

    public static Lang detectLang(String text) {
        if (detectRussian(text)) {
            return Lang.RUSSIAN;
        }

        return Lang.UNKNOWN;
    }

    public static boolean detectRussian(String text) {
        // Simple threshold - if more than 30% Russian words, consider it Russian
        return calculateRussianConfidence(text) > 30;
    }

    public static double calculateRussianConfidence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }

        // Count total words
        String[] words = text.split("\\s+");
        int totalWords = words.length;
        if (totalWords == 0) {
            return 0.0;
        }

        // Counter for Russian words
        int russianWords = 0;

        for (String word : words) {
            word = word.replaceAll("[^а-яА-ЯёЁa-zA-Z]", ""); // Remove non-alphabetic characters

            if (word.isEmpty()) {
                continue;
            }

            // Check if the word contains Cyrillic characters
            Matcher cyrillicMatcher = CYRILLIC_PATTERN.matcher(word);
            if (cyrillicMatcher.find()) {
                russianWords++;
                continue;
            }

            // Check for common Russian words
            Matcher commonWordsMatcher = COMMON_WORDS_PATTERN.matcher(word);
            if (commonWordsMatcher.matches()) {
                russianWords++;
                continue;
            }

            // Check for common Russian word endings
            Matcher endingsMatcher = RUSSIAN_ENDINGS_PATTERN.matcher(word);
            if (endingsMatcher.find()) {
                russianWords++;
            }
        }

        // Calculate percentage of Russian words
        return (double) russianWords / totalWords * 100;
    }

    public enum Lang {

        RUSSIAN,

        ENGLISH,

        CHINESE,

        UNKNOWN

    }

}
