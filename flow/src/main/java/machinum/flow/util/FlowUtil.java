package machinum.flow.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class providing various helper methods for flow operations,
 * including ID generation, token counting, text processing, and duration formatting.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowUtil {

    /**
     * The encoding used for token counting operations.
     */
    public static final Encoding ENCODING = Encodings.newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    /**
     * Generates a new unique identifier with the specified prefix.
     *
     * @param prefix the prefix to prepend to the generated ID
     * @return a unique ID string consisting of the prefix followed by a random hexadecimal value
     * @throws NullPointerException if the prefix is null
     */
    public static String newId(String prefix) {
        return prefix + Long.toHexString(Double.doubleToLongBits(Math.random()));
    }

    /**
     * Accepts a function that extracts a {@link java.lang.Comparable
     * Comparable} sort key from a type {@code T}, and returns a {@code
     * Comparator<T>} that compares by that sort key in reverse order.
     *
     * <p>The returned comparator is serializable if the specified function
     * is also serializable.
     *
     * @param <T>          the type of element to be compared
     * @param <U>          the type of the {@code Comparable} sort key
     * @param keyExtractor the function used to extract the {@link
     *                     Comparable} sort key
     * @return a comparator that compares by an extracted key in reverse order
     * @throws NullPointerException if the argument is null
     * @apiNote For example, to obtain a {@code Comparator} that compares {@code
     * Person} objects by their last name in reverse order,
     *
     * <pre>{@code
     *     Comparator<Person> byLastNameReverse = ReverseComparator.comparingReverse(Person::getLastName);
     * }</pre>
     */
    public static <T, U extends Comparable<? super U>> Comparator<T> comparingReverse(
            Function<? super T, ? extends U> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
                (c1, c2) -> keyExtractor.apply(c2).compareTo(keyExtractor.apply(c1));
    }

    /**
     * Counts the number of tokens in the given text using the CL100K_BASE encoding.
     *
     * @param text the text to count tokens for
     * @return the number of tokens in the text
     * @throws NullPointerException if the text is null
     */
    public static Integer countTokens(String text) {
        return ENCODING
                .encode(text)
                .boxed()
                .size();
    }

    /**
     * Counts the number of words in the given text.
     * Words are separated by whitespace characters.
     *
     * @param text the text to count words for, can be null or empty
     * @return the number of words in the text, 0 if text is null or empty
     */
    public static Integer countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int wordCount = 0;
        boolean inWord = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (Character.isWhitespace(c)) {
                inWord = false;
            } else {
                if (!inWord) {
                    wordCount++;
                    inWord = true;
                }
            }
        }

        return wordCount;
    }

    /**
     * Splits the given list into chunks of the specified length.
     *
     * @param <T>    the type of elements in the list
     * @param source the list to split into chunks
     * @param length the maximum size of each chunk, must be positive
     * @return a list of lists, where each inner list is a chunk of the original list
     * @throws IllegalArgumentException if length is not positive
     * @throws NullPointerException     if source is null
     */
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

    /**
     * Rethrows the given throwable, allowing checked exceptions to be thrown
     * without declaring them in the method signature.
     *
     * @param <T> the return type (never actually returned)
     * @param throwable the throwable to rethrow
     * @return never returns, always throws the throwable
     * @throws Throwable the rethrown throwable
     */
    @SneakyThrows
    public static <T> T rethrow(Throwable throwable) {
        throw throwable;
    }

    /**
     * Converts the given duration to a formatted string representation.
     *
     * @param duration the duration to convert
     * @return a string in the format "hh:mm:ss.mmm (hh:mm:ss.mmm)" representing the duration
     * @throws NullPointerException if duration is null
     */
    public static String toString(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        return String.format("%02d:%02d:%02d.%03d (hh:mm:ss.mmm)", hours, minutes, seconds, millis);
    }

}
