package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextReportUtil {

    public static TextReport analyzeText(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        String[] lines = text.split("\r?\n");
        String[] words = text.split("\\W+");

        int textLength = text.length();
        int tokensLength = TextUtil.countTokens(text);
        int wordCount = words.length;
        int lineCount = lines.length;
        double averageWordLength = Arrays.stream(words).mapToInt(String::length).average().orElse(0);
        String longestWord = Arrays.stream(words).max(Comparator.comparingInt(String::length)).orElse("");
        String shortestWord = Arrays.stream(words).filter(w -> !w.isEmpty()).min(Comparator.comparingInt(String::length)).orElse("");
        int sentenceCount = text.split("[.!?]").length;

        Map<String, Long> wordFrequency = Arrays.stream(words)
                .filter(w -> !w.isEmpty())
                .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));

        String mostFrequentWord = wordFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        List<String> leastFrequentWords = wordFrequency.entrySet().stream()
                .filter(e -> e.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Character, Long> characterFrequency = text.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        int whitespaceCount = (int) text.chars().filter(Character::isWhitespace).count();
        int digitCount = (int) text.chars().filter(Character::isDigit).count();
        int alphabeticCharacterCount = (int) text.chars().filter(Character::isAlphabetic).count();
        int uppercaseCount = (int) text.chars().filter(Character::isUpperCase).count();
        int lowercaseCount = (int) text.chars().filter(Character::isLowerCase).count();
        int punctuationCount = (int) text.chars().filter(c -> ",.?!:;".indexOf(c) != -1).count();
        int vowelCount = (int) text.chars().filter(c -> "AEIOUaeiouАЕЁИОУЫЭЮЯаеёиоуыэюя".indexOf(c) != -1).count();
        int consonantCount = alphabeticCharacterCount - vowelCount;
        double estimatedReadingTime = wordCount / 200.0; // Assuming 200 words per minute.
        int uniqueWordCount = wordFrequency.size();

        double textDensity = wordCount / (double) lineCount;
        int paragraphCount = text.split("\r?\n\r?\n").length;
        double averageLineLength = Arrays.stream(lines).mapToInt(String::length).average().orElse(0);
        int capitalLettersCount = (int) text.chars().filter(Character::isUpperCase).count();
        double alphanumericRatio = (alphabeticCharacterCount + digitCount) / (double) textLength;
        int specialCharacterCount = textLength - (alphabeticCharacterCount + digitCount + whitespaceCount);

        return new TextReport(textLength, tokensLength, wordCount, lineCount, averageWordLength, longestWord, shortestWord,
                sentenceCount, mostFrequentWord, leastFrequentWords, characterFrequency, whitespaceCount, digitCount,
                alphabeticCharacterCount, uppercaseCount, lowercaseCount, punctuationCount, vowelCount, consonantCount,
                estimatedReadingTime, uniqueWordCount, textDensity, paragraphCount, averageLineLength, capitalLettersCount,
                alphanumericRatio, specialCharacterCount);
    }

    public static class TextReport {
        private final int textLength;
        private final int tokensLength;
        private final int wordCount;
        private final int lineCount;
        private final double averageWordLength;
        private final String longestWord;
        private final String shortestWord;
        private final int sentenceCount;
        private final String mostFrequentWord;
        private final List<String> leastFrequentWords;
        private final Map<Character, Long> characterFrequency;
        private final int whitespaceCount;
        private final int digitCount;
        private final int alphabeticCharacterCount;
        private final int uppercaseCount;
        private final int lowercaseCount;
        private final int punctuationCount;
        private final int vowelCount;
        private final int consonantCount;
        private final double estimatedReadingTime;
        private final int uniqueWordCount;
        private final double textDensity;
        private final int paragraphCount;
        private final double averageLineLength;
        private final int capitalLettersCount;
        private final double alphanumericRatio;
        private final int specialCharacterCount;

        public TextReport(int textLength, int tokensLength, int wordCount, int lineCount, double averageWordLength, String longestWord, String shortestWord,
                          int sentenceCount, String mostFrequentWord, List<String> leastFrequentWords, Map<Character, Long> characterFrequency,
                          int whitespaceCount, int digitCount, int alphabeticCharacterCount, int uppercaseCount, int lowercaseCount,
                          int punctuationCount, int vowelCount, int consonantCount, double estimatedReadingTime, int uniqueWordCount,
                          double textDensity, int paragraphCount, double averageLineLength, int capitalLettersCount, double alphanumericRatio,
                          int specialCharacterCount) {
            this.textLength = textLength;
            this.tokensLength = tokensLength;
            this.wordCount = wordCount;
            this.lineCount = lineCount;
            this.averageWordLength = averageWordLength;
            this.longestWord = longestWord;
            this.shortestWord = shortestWord;
            this.sentenceCount = sentenceCount;
            this.mostFrequentWord = mostFrequentWord;
            this.leastFrequentWords = leastFrequentWords;
            this.characterFrequency = characterFrequency;
            this.whitespaceCount = whitespaceCount;
            this.digitCount = digitCount;
            this.alphabeticCharacterCount = alphabeticCharacterCount;
            this.uppercaseCount = uppercaseCount;
            this.lowercaseCount = lowercaseCount;
            this.punctuationCount = punctuationCount;
            this.vowelCount = vowelCount;
            this.consonantCount = consonantCount;
            this.estimatedReadingTime = estimatedReadingTime;
            this.uniqueWordCount = uniqueWordCount;
            this.textDensity = textDensity;
            this.paragraphCount = paragraphCount;
            this.averageLineLength = averageLineLength;
            this.capitalLettersCount = capitalLettersCount;
            this.alphanumericRatio = alphanumericRatio;
            this.specialCharacterCount = specialCharacterCount;
        }

        public String toMarkdown() {
            String md = "|Key|Value|  \n" +
                    "|--------|---|  \n" +
                    "| Text Length (characters) | " + textLength + " |\n" +
                    "| Tokens Length | " + tokensLength + " |\n" +
                    "| Word Count | " + wordCount + " |\n" +
                    "| Line Count | " + lineCount + " |\n" +
                    "| Average Word Length | " + formatDouble(averageWordLength) + " |\n" +
                    "| Longest Word | " + longestWord + " |\n" +
                    "| Shortest Word | " + shortestWord + " |\n" +
                    "| Sentence Count | " + sentenceCount + " |\n" +
                    "| Most Frequent Word | " + mostFrequentWord + " |\n" +
//            md.append("| Least Frequent Words | ").append(String.join(", ", leastFrequentWords)).append(" |\n");
//            md.append("| Character Frequency | ").append(characterFrequency).append(" |\n");
                    "| Whitespace Count | " + whitespaceCount + " |\n" +
                    "| Digit Count | " + digitCount + " |\n" +
                    "| Alphabetic Character Count | " + alphabeticCharacterCount + " |\n" +
                    "| Uppercase Letters Count | " + uppercaseCount + " |\n" +
                    "| Lowercase Letters Count | " + lowercaseCount + " |\n" +
                    "| Punctuation Count | " + punctuationCount + " |\n" +
                    "| Vowel Count | " + vowelCount + " |\n" +
                    "| Consonant Count | " + consonantCount + " |\n" +
                    "| Estimated Reading Time (minutes) | " + formatDouble(estimatedReadingTime) + " |\n" +
                    "| Unique Word Count | " + uniqueWordCount + " |\n" +
                    "| Text Density | " + formatDouble(textDensity) + " |\n" +
                    "| Paragraph Count | " + paragraphCount + " |\n" +
                    "| Average Line Length | " + formatDouble(averageLineLength) + " |\n" +
                    "| Capital Letters Count | " + capitalLettersCount + " |\n" +
                    "| Alphanumeric Ratio | " + formatDouble(alphanumericRatio) + " |\n" +
                    "| Special Character Count | " + specialCharacterCount + " |\n";
            return md;
        }

        private String formatDouble(double value) {
            return String.format("%.2f", value);
        }

        public String compare(TextReport other) {
            return "| Metric | Origin | New | Difference |\n" +
                    "|--------|----------|----------|------------|\n" +
                    "| Text Length | " + textLength + " | " + other.textLength + " | " + checkInt(textLength - other.textLength) + " |\n" +
                    "| Tokens Length | " + tokensLength + " | " + other.tokensLength + " | " + checkInt(tokensLength - other.tokensLength) + " |\n" +
                    "| Word Count | " + wordCount + " | " + other.wordCount + " | " + checkInt(wordCount - other.wordCount) + " |\n" +
                    "| Line Count | " + lineCount + " | " + other.lineCount + " | " + checkInt(lineCount - other.lineCount) + " |\n" +
                    "| Average Word Length | " + formatDouble(averageWordLength) + " | " + formatDouble(other.averageWordLength) + " | " + formatDouble(averageWordLength - other.averageWordLength) + " |\n" +
                    "| Longest Word | " + longestWord + " | " + other.longestWord + " | " + compareStrings(longestWord, other.longestWord) + " |\n" +
                    "| Shortest Word | " + shortestWord + " | " + other.shortestWord + " | " + compareStrings(shortestWord, other.shortestWord) + " |\n" +
                    "| Sentence Count | " + sentenceCount + " | " + other.sentenceCount + " | " + checkInt(sentenceCount - other.sentenceCount) + " |\n" +
                    "| Most Frequent Word | " + mostFrequentWord + " | " + other.mostFrequentWord + " | " + compareStrings(mostFrequentWord, other.mostFrequentWord) + " |\n" +
                    "| Whitespace Count | " + whitespaceCount + " | " + other.whitespaceCount + " | " + checkInt(whitespaceCount - other.whitespaceCount) + " |\n" +
                    "| Digit Count | " + digitCount + " | " + other.digitCount + " | " + checkInt(digitCount - other.digitCount) + " |\n" +
                    "| Alphabetic Character Count | " + alphabeticCharacterCount + " | " + other.alphabeticCharacterCount + " | " + checkInt(alphabeticCharacterCount - other.alphabeticCharacterCount) + " |\n" +
                    "| Uppercase Letters Count | " + uppercaseCount + " | " + other.uppercaseCount + " | " + checkInt(uppercaseCount - other.uppercaseCount) + " |\n" +
                    "| Lowercase Letters Count | " + lowercaseCount + " | " + other.lowercaseCount + " | " + checkInt(lowercaseCount - other.lowercaseCount) + " |\n" +
                    "| Punctuation Count | " + punctuationCount + " | " + other.punctuationCount + " | " + checkInt(punctuationCount - other.punctuationCount) + " |\n" +
                    "| Vowel Count | " + vowelCount + " | " + other.vowelCount + " | " + checkInt(vowelCount - other.vowelCount) + " |\n" +
                    "| Consonant Count | " + consonantCount + " | " + other.consonantCount + " | " + checkInt(consonantCount - other.consonantCount) + " |\n" +
                    "| Estimated Reading Time (minutes) | " + formatDouble(estimatedReadingTime) + " | " + formatDouble(other.estimatedReadingTime) + " | " + formatDouble(estimatedReadingTime - other.estimatedReadingTime) + " |\n" +
                    "| Unique Word Count | " + uniqueWordCount + " | " + other.uniqueWordCount + " | " + checkInt(uniqueWordCount - other.uniqueWordCount) + " |\n" +
                    "| Text Density | " + formatDouble(textDensity) + " | " + formatDouble(other.textDensity) + " | " + formatDouble(textDensity - other.textDensity) + " |\n" +
                    "| Paragraph Count | " + paragraphCount + " | " + other.paragraphCount + " | " + checkInt(paragraphCount - other.paragraphCount) + " |\n" +
                    "| Average Line Length | " + formatDouble(averageLineLength) + " | " + formatDouble(other.averageLineLength) + " | " + formatDouble(averageLineLength - other.averageLineLength) + " |\n" +
                    "| Capital Letters Count | " + capitalLettersCount + " | " + other.capitalLettersCount + " | " + checkInt(capitalLettersCount - other.capitalLettersCount) + " |\n" +
                    "| Alphanumeric Ratio | " + formatDouble(alphanumericRatio) + " | " + formatDouble(other.alphanumericRatio) + " | " + formatDouble(alphanumericRatio - other.alphanumericRatio) + " |\n" +
                    "| Special Character Count | " + specialCharacterCount + " | " + other.specialCharacterCount + " | " + checkInt(specialCharacterCount - other.specialCharacterCount) + " |\n"
                    ;
        }

        private String compareStrings(String a, String b) {
            return a.compareTo(b) == 0 ? "" : "**diff**";
        }

        private String checkInt(int value) {
            return value < 0 ? String.valueOf(value * -1) : "**%s**".formatted(value * -1);
        }

        private String checkDouble(double value) {
            return value < 0 ? "*%s*".formatted(value) : String.valueOf(value);
        }

    }

}
