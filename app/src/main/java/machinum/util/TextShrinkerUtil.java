package machinum.util;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextShrinkerUtil {

    public static final int DEFAULT_THRESHOLD = 2;
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("([^.!?]+[.!?])");

    public static String shrinkText(String text, int shrinkPercent) {
        if (text == null || text.isEmpty() || shrinkPercent <= 0 || shrinkPercent >= 100) {
            return text;
        }

        List<Sentence> sentences = extractSentences(text);
        int currentLength = text.length();
        int targetLength = (currentLength * (100 - shrinkPercent)) / 100;

        List<Sentence> toRemove = selectSentencesToRemove(sentences, currentLength - targetLength);

        return rebuildText(text, toRemove);
    }

    private static List<Sentence> extractSentences(String text) {
        List<Sentence> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);

        while (matcher.find()) {
            sentences.add(new Sentence(
                    matcher.group(),
                    matcher.start(),
                    matcher.end(),
                    matcher.group().length()
            ));
        }

        return sentences;
    }

    private static List<Sentence> selectSentencesToRemove(List<Sentence> sentences, int targetReduction) {
        List<Sentence> toRemove = new ArrayList<>();
        int currentReduction = 0;

        // Find duplicates first
        Map<String, List<Sentence>> duplicates = sentences.stream()
                .collect(Collectors.groupingBy(s -> s.getText().trim().toLowerCase()));

        duplicates.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .forEach(e -> {
                    e.getValue().stream()
                            .skip(1)
                            .forEach(toRemove::add);
                });

        currentReduction = toRemove.stream()
                .mapToInt(Sentence::getLength)
                .sum();

        if (currentReduction >= targetReduction) {
            return toRemove;
        }

        // Sort remaining sentences by length
        List<Sentence> remaining = sentences.stream()
                .filter(s -> !toRemove.contains(s))
                .sorted()
                .toList();

        int left = 0;
        int right = remaining.size() - 1;

        while (currentReduction < targetReduction && left < right) {
            if (left % 2 == 0) {
                Sentence candidate = remaining.get(left);
                if (hasNotNearbyRemoved(candidate, toRemove, DEFAULT_THRESHOLD)) {
                    toRemove.add(candidate);
                    currentReduction += candidate.getLength();
                }
                left++;
            } else {
                Sentence candidate = remaining.get(right);
                if (hasNotNearbyRemoved(candidate, toRemove, DEFAULT_THRESHOLD)) {
                    toRemove.add(candidate);
                    currentReduction += candidate.getLength();
                }
                right--;
            }
        }

        return toRemove;
    }

    private static boolean hasNotNearbyRemoved(Sentence candidate, List<Sentence> removed, int threshold) {
        return removed.stream()
                .noneMatch(r -> Math.abs(r.getStartPosition() - candidate.getStartPosition()) <
                        threshold * candidate.getLength());
    }

    private static String rebuildText(String originalText, List<Sentence> toRemove) {
        if (toRemove.isEmpty()) {
            return originalText;
        }

        StringBuilder result = new StringBuilder();
        int currentPos = 0;

        List<Sentence> sortedRemoved = toRemove.stream()
                .sorted(Comparator.comparingInt(Sentence::getStartPosition))
                .toList();

        for (Sentence removed : sortedRemoved) {
            result.append(originalText, currentPos, removed.getStartPosition());
            result.append("\n...");
            currentPos = removed.getEndPosition();
        }

        if (currentPos < originalText.length()) {
            result.append(originalText.substring(currentPos));
        }

        return result.toString();
    }

    @Data
    @AllArgsConstructor
    private static class Sentence implements Comparable<Sentence> {

        private String text;
        private int startPosition;
        private int endPosition;
        private int length;

        @Override
        public int compareTo(Sentence other) {
            return Integer.compare(this.length, other.length);
        }

    }

}
