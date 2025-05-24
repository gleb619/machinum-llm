package machinum.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.*;
import machinum.model.core.StringContainer;
import machinum.processor.core.StringSupport;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Content;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Pair;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextUtil {

    public static final Encoding ENCODING = Encodings.newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    public static String firstNotEmpty(String... inputs) {
        for (String input : inputs) {
            if (Objects.nonNull(input) && !input.trim().isBlank()) {
                return input;
            }
        }

        throw new NullPointerException("All arguments is null");
    }

    public static boolean isNotEmpty(String input) {
        return !isEmpty(input);
    }

    public static boolean isEmpty(String input) {
        return Objects.isNull(input) || input.isBlank();
    }

    public static int length(String input) {
        return isNotEmpty(input) ? input.length() : 0;
    }

    public static String toShortDescription(String input) {
        return toShortDescription(input, 30);
    }

    public static String toShortDescription(String input, int length) {
        if (Objects.isNull(input) || input.isEmpty()) {
            return "<null>";
        }

        var text = input.trim()
                .replaceAll("[\r\n]", "");

        return text
                .substring(0, Math.min(text.length(), length));
    }

    public static String toShortDescription(Collection<String> list) {
        return list.stream()
                .map(input -> "%s...; [%5d tokens|%5d words]".formatted(toShortDescription(input), countTokens(input), countWords(input)))
                .collect(Collectors.joining("\n"));
    }

    public static <T extends StringSupport> String toShortStringDescription(List<T> list) {
        return toShortDescription(list.stream()
                .map(StringSupport::stringValue)
                .collect(Collectors.toList()));
    }

    public static String toShortHistoryDescription(List<Message> history) {
        return toShortDescription(history.stream()
                .map(Message::getText)
                .collect(Collectors.toList()));
    }

    public static String indent(String input) {
        return indent(input, 2);
    }

    public static String indent(String text, Integer spaces) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder indentedText = new StringBuilder();
        String indentation = " ".repeat(Math.max(0, spaces));
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            indentedText.append(indentation).append(lines[i]);
            if (i < lines.length - 1) {
                indentedText.append("\n");
            }
        }

        return indentedText.toString();
    }

    @Deprecated
    public static List<String> assistantChunks(List<Message> messages) {
        return historyToChunks(messages, AssistantMessage.class);
    }

    @Deprecated
    public static List<String> userChunks(List<Message> messages) {
        return historyToChunks(messages, UserMessage.class);
    }

    @Deprecated
    private static List<String> historyToChunks(List<Message> messages, Class<?> clazz) {
        return messages.stream()
                .filter(m -> clazz.isAssignableFrom(m.getClass()))
                .map(Content::getText)
                .collect(Collectors.toList());
    }

    @Deprecated
    public static String toText(List<Message> messages) {
        return messages.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));
    }

    @Deprecated
    public static List<Integer> toChunkSize(List<String> chunks) {
        return chunks.stream().map(TextUtil::countTokensFast).collect(Collectors.toList());
    }

    public static Integer countLines(String text) {
        return (int) text.trim().lines().count();
    }

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

    public static Integer countHistoryWords(List<Message> history) {
        return countWords(history.stream()
                .map(Message::getText)
                .collect(Collectors.toList()));
    }

    public static Integer countWords(List<String> history) {
        return countWords(String.join("\n", history));
    }

    @Deprecated
    public static Integer countTokensFast(String text) {
        return countWords(text) * 4;
    }

    public static Integer countTokens(String text) {
        return ENCODING
                .encode(text)
                .boxed()
                .size();
    }

    @SneakyThrows
    public static Integer countTokens(Resource resource) {
        @Cleanup InputStream inputStream = resource.getInputStream();
        return countTokens(StreamUtils.copyToString(inputStream, Charset.defaultCharset()));
    }

    public static Integer countHistoryTokens(List<Message> history) {
        return countTokens(history.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n")));
    }

    @Deprecated
    public static Integer countHistoryTokensFast(List<Message> history) {
        return countTokensFast(history.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n")));
    }

    public static Integer countTokens(List<String> list) {
        return countTokensFast(String.join("", list));
    }

    public static <T extends StringContainer> Integer countTokensInContainer(List<T> containers) {
        var text = containers.stream()
                .map(StringContainer::representation)
                .collect(Collectors.joining(""));

        return countTokensFast(text);
    }

    public static List<List<String>> split(List<String> originalList, int partitionSize) {
        var partitions = new ArrayList<List<String>>();
        for (int i = 0; i < originalList.size(); i += partitionSize) {
            partitions.add(originalList.subList(i,
                    Math.min(i + partitionSize, originalList.size())));
        }

        return partitions;
    }

    public static String truncateFromHead(@NonNull String text) {
        var truncated = truncateFromHead(text.lines().collect(Collectors.toList()), 5);
        return String.join("\n", truncated);
    }

    /**
     * Truncates a list of strings from the beginning if it exceeds the maximum number of lines.
     * If the list size is less than or equal to the maximum lines, the original list is returned unmodified.
     * Otherwise, it removes elements from the beginning until the list has exactly maxLines elements.
     *
     * @param lines    The list of strings to truncate
     * @param maxLines The maximum number of lines to keep
     * @return A new list containing at most maxLines strings, preserving the most recent ones
     */
    public static List<String> truncateFromHead(@NonNull List<String> lines, int maxLines) {
        // Validate maxLines parameter
        if (maxLines < 0) {
            throw new IllegalArgumentException("maxLines must be non-negative");
        }

        // If the list is empty or already smaller than or equal to maxLines, return it as is
        if (lines.isEmpty() || lines.size() <= maxLines) {
            return new ArrayList<>(lines);
        }

        // Calculate how many elements to skip from the beginning
        int startIndex = lines.size() - maxLines;

        // Return a new list with only the maxLines most recent elements
        return new ArrayList<>(lines.subList(startIndex, lines.size()));
    }

    /**
     * Truncates a list of strings from the start if its total length exceeds the specified limit.
     * The method preserves the most recent entries by removing items from the beginning.
     *
     * @param strings             List of strings to process
     * @param maxCharactersLength Maximum allowed total length of all strings combined
     * @return A new list containing strings that fit within the specified limit
     * @throws IllegalArgumentException if maxLength is negative
     */
    public static List<String> truncateFromStart(List<String> strings, int maxCharactersLength) {
        if (strings.size() <= maxCharactersLength) {
            return strings;
        }

        var input = strings.stream()
                .map(StringContainer::of)
                .collect(Collectors.toList());
        var output = truncateContainer(input, maxCharactersLength).stream()
                .map(StringContainer::toString)
                .collect(Collectors.toList());

        return output;
    }

    public static <U> List<StringContainer<U>> truncateContainer(List<StringContainer<U>> strings, int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Max length cannot be negative");
        }

        if (strings == null || strings.isEmpty()) {
            return new ArrayList<>();
        }

        // Calculate total length
        int totalLength = countTokensInContainer(strings);

        if (totalLength <= maxLength) {
            return new ArrayList<>(strings);
        }

        var result = new ArrayList<>(strings);

        // Remove items from start until we fit the limit
        while (!result.isEmpty() && countTokensInContainer(result) > maxLength) {
            result.remove(0);
        }

        return result;
    }

    public static List<String> detectLost(Collection<String> oldList, Collection<String> newList) {
        var uniques = new ArrayList<>(oldList);
        uniques.removeAll(newList);

        return uniques;
    }

    public static String valueOf(Object value) {
        return String.valueOf(value);
    }

    public static <I, O> Function<I, String> valueOf(Function<I, O> target) {
        Objects.requireNonNull(target);
        return o -> TextUtil.valueOf(target.apply(o));
    }

    public static String shrinkText(String text, double percent) {
        return shrinkText(text, Integer.valueOf((int) percent));
    }

    public static String shrinkText(String text, Integer percent) {
        return TextShrinkerUtil.shrinkText(text, percent);
    }

    //TODO move to compression strategy
    @Deprecated
    public List<Message> truncateHistory(List<Message> history, int maxLength) {
        var localHistory = new ArrayList<>(history);
        var output = new ArrayList<Message>();
        var containers = new ArrayList<StringContainer<Pair<Message, Message>>>();
        var message = localHistory.getFirst();

        if (message instanceof SystemMessage) {
            output.add(localHistory.removeFirst());
        }

        for (int i = 0; i < localHistory.size(); i = i + 2) {
            var first = localHistory.get(i);
            var second = localHistory.get(i + 1);
            var stringContainer = StringContainer.of(first, second);

            containers.add(stringContainer);
        }

        var stringContainers = truncateContainer(containers, maxLength);
        for (var container : stringContainers) {
            var pair = container.value();
            output.add(pair.getFirst());
            output.add(pair.getSecond());
        }

        return output;
    }

}
