package machinum.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.NonFinal;
import machinum.exception.AppIllegalStateException;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static machinum.util.TextUtil.detectLost;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JavaUtil {

    private static final int DEFAULT_PAGE_SIZE = 10;

    public static <T> T firstNotNull(T... objects) {
        for (T input : objects) {
            if (Objects.nonNull(input)) {
                return input;
            }
        }

        throw new NullPointerException("All arguments is null");
    }

    public static <T> Function<T, T> combine(
            Function<T, T>... functions) {

        return Arrays.stream(functions)
                .reduce(Function.identity(), Function::andThen);
    }

    public static double calculatePercent(double part, String total) {
        return calculatePercent(part, total.length());
    }

    public static double calculatePart(double percent, String total) {
        return calculatePart(percent, total.length());
    }

    /**
     * Calculates the percentage value from a part and total.
     *
     * @param part  The partial value
     * @param total The total value
     * @return The percentage value (0-100)
     * @throws IllegalArgumentException if total is zero
     */
    public static double calculatePercent(double part, double total) {
        if (total == 0) {
            throw new IllegalArgumentException("Total cannot be zero");
        }
        return (part / total) * 100.0;
    }

    /**
     * Calculates the part value from a percentage and total.
     *
     * @param percent The percentage value (0-100)
     * @param total   The total value
     * @return The part value
     * @throws IllegalArgumentException if percentage is not between 0 and 100
     */
    public static double calculatePart(double percent, double total) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }
        return (percent / 100.0) * total;
    }

    public static Integer calculatePercentDifference(Integer num1, Integer num2) {
        return (int) Math.ceil(calculatePercentDifference((double) num1, (double) num2));
    }

    public static Double calculatePercentDifference(Double num1, Double num2) {
        if (num2 == 0) {
            return Double.NaN;
        }
        return Math.abs(((num1 - num2) / num2) * 100);
    }

    @Deprecated
    public static double calculateReductionForChunk(@NonFinal String text, int maxLength) {
        var tokens = TextUtil.countTokensFast(text);
        return calculateReductionForToken(maxLength, tokens);
    }

    public static double calculateReductionForToken(int maxLength, Integer tokens) {
        if (tokens <= maxLength) {
            return 0.0;
        }

        int diff = tokens - maxLength;
        return (diff / (double) maxLength) * 100.0;
    }

    public static List<Double> calculateReductionForChunks(List<String> chunks, int maxTokenLength) {
        var chunkTokens = chunks.stream()
                .map(TextUtil::countTokensFast)
                .toList();

        return calculateReductionForTokens(chunkTokens, maxTokenLength);
    }

    public static List<Double> calculateReductionForTokens(List<Integer> chunkTokens, int maxTokenLength) {
        int totalLength = chunkTokens.stream().mapToInt(value -> value).sum();
        if (totalLength <= maxTokenLength) {
            return chunkTokens.stream().map(chunk -> 0.0).collect(Collectors.toList());
        }

        int excess = totalLength - maxTokenLength;
        return chunkTokens.stream()
                .map(chunk -> (chunk / (double) totalLength) * excess * 100.0 / chunk)
                .collect(Collectors.toList());
    }

    @Deprecated
    public static List<Integer> splitIntoSimilarParts(int first, int second) {
        if (first <= second || second <= 0) {
            return List.of(first);
        }

        List<Integer> result = new ArrayList<>();
        int remainingValue = first;
        int parts = (int) Math.ceil((double) first / second);
        int baseValue = first / parts;

        while (remainingValue > 0) {
            if (result.size() == parts - 1) {
                result.add(remainingValue);
                break;
            }

            int currentValue = Math.min(baseValue, remainingValue);
            double deviation = Math.abs((double) (currentValue - baseValue) / baseValue);

            if (deviation > 0.1) {
                // Recalculate with more parts if deviation is too high
                parts++;
                baseValue = first / parts;
                result.clear();
                remainingValue = first;
                continue;
            }

            result.add(currentValue);
            remainingValue -= currentValue;
        }

        return result;
    }

    /**
     * Checks if the provided string is valid JSON.
     *
     * @param jsonString The string to validate
     * @return true if the string is valid JSON, false otherwise
     * @throws IllegalArgumentException if jsonString is null
     */
    public static boolean isValidJson(@NonNull String jsonString) {
        var objectMapper = new ObjectMapper();
        if (jsonString.trim().isEmpty()) {
            return false;
        }

        try {
            // Configure parser to be strict
            objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, false);
            objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, false);
            objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, false);

            // Parse the string
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Deprecated
    public static boolean isJsonArray(@NonNull String jsonString) {
        return jsonString.trim().startsWith("[");
    }

    public static <T> List<T> mutableListOf(List<T> parent, T... args) {
        var output = new ArrayList<>(parent);
        output.addAll(Arrays.asList(args));

        return output;
    }

    public static <T> List<T> mutableListOf(T... args) {
        return new ArrayList<>(List.of(args));
    }

    public static Integer parseInt(Object o) {
        return Integer.parseInt(String.valueOf(o));
    }

    public static String newId(String prefix) {
        return prefix + Long.toHexString(Double.doubleToLongBits(Math.random()));
    }

    @Deprecated
    public static <T> List<T> uniqueJoin(List<T> first, List<T> second) {
        var result = new HashSet<>(first);
        result.addAll(second);
        return new ArrayList<>(result);
    }

    public static <T, K> List<T> uniqueItems(Collection<T> list1, Collection<T> list2, Function<T, K> extractor) {
        var set1 = new HashSet<>(list1).stream()
                .collect(Collectors.toMap(extractor, Function.identity(), (f, s) -> f));

        return list2.stream()
                .filter(item -> !set1.containsKey(extractor.apply(item)))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Returns a new list containing only unique elements based on the result of the provided function.
     * When multiple elements map to the same key via the function, only the first occurrence is kept.
     *
     * @param <T>          the type of elements in the input list
     * @param <R>          the type of the key extracted by the function
     * @param list         the input list
     * @param keyExtractor function to extract a key used for uniqueness comparison
     * @return a new list with unique elements based on the extracted key
     */
    public static <T, R> List<T> uniqueBy(List<T> list, Function<T, R> keyExtractor) {
        Map<R, Boolean> seen = new HashMap<>();
        List<T> result = new ArrayList<>();

        for (T element : list) {
            R key = keyExtractor.apply(element);
            if (!seen.containsKey(key)) {
                seen.put(key, true);
                result.add(element);
            }
        }

        return result;
    }

    public static <T> List<List<T>> toChunks(List<T> source, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length = " + length);
        }

        int size = source.size();
        if (size <= 0) {
            return Collections.emptyList();
        }
        if (size <= length) {
            return List.of(source);
        }

        int fullChunks = (size - 1) / length;

        return IntStream.range(0, fullChunks + 1).mapToObj(
                        n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length))
                .collect(Collectors.toList());
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static <T, R> T findBy(List<T> list, Function<T, R> keyExtractor, R awaitedValue) {
        for (T item : list) {
            var value = keyExtractor.apply(item);
            if (Objects.equals(value, awaitedValue)) {
                return item;
            }
        }

        return null;
    }

    public static Map<String, String> createSuitableMap(List<String> originList, List<String> translatedList) {
        var lostNames = detectLost(originList, translatedList);
        var unsuitableNames = new ArrayList<>(detectLost(translatedList, originList));
        var resultMap = new HashMap<String, String>();

        for (var lostName : lostNames) {
            var possibleObjects = TextSimilarityUtil.search(unsuitableNames, lostName);

            if (!possibleObjects.isEmpty()) {
                var bestMatch = possibleObjects.getFirst();
                resultMap.put(lostName, bestMatch);
                unsuitableNames.remove(bestMatch);
            }
        }

        return resultMap;
    }

    @SneakyThrows
    public static String readResource(String path) {
        var resource = new ClassPathResource(path);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static <T, R> List<T> cut(
            List<T> originalList,
            Function<List<T>, R> metricCalculator,
            Predicate<R> conditionChecker) {

        // Create a mutable copy of the original list to perform removals on.
        // The originalList remains untouched.
        List<T> currentList = new ArrayList<>(originalList);
//        currentList.stream().filter()

        // Continuously remove the last element from the list as long as:
        // 1. The list is not empty (to prevent IndexOutOfBoundsException).
        // 2. The condition, checked by conditionChecker, is NOT met for the current metric.
        //    If the condition IS met, we stop removing.
        while (!currentList.isEmpty() && !conditionChecker.test(metricCalculator.apply(currentList))) {
            currentList.remove(currentList.size() - 1); // Remove the element at the last index
        }

        // Return the (potentially modified) new list.
        // If the loop finished because the condition was met, this is the cut list.
        // If the loop finished because the list became empty, this is an empty list.
        return currentList;
    }

    /**
     * Calculate MD5 hash for line content, matching the logic used in materialized view
     */
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }

    public static Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            throw new IllegalArgumentException("Input string is null or empty");
        }

        durationStr = durationStr.trim();
        char unit = durationStr.charAt(durationStr.length() - 1);
        var valueStr = durationStr.substring(0, durationStr.length() - 1).trim();

        if (!valueStr.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid number format in duration string");
        }

        long value = Long.parseLong(valueStr);

        return switch (unit) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'w' -> Duration.ofDays(value * 7);
            default -> throw new IllegalArgumentException("Unsupported time unit: " + unit);
        };
    }

    /**
     * Adds a small delay between execution.
     *
     * @param millis
     * @throws AppIllegalStateException if the thread is interrupted during sleep
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AppIllegalStateException("Work interrupted", ie);
        }
    }

}
