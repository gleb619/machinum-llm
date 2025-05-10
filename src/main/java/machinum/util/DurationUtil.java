package machinum.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.processor.core.MarkdownSupport;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static machinum.config.Constants.ARGUMENT;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DurationUtil {

    private static final DurationConfig INSTANCE = new DurationConfig(List.of());

    public static DurationConfig getInstance() {
        return INSTANCE;
    }

    public static DurationConfig configure(DurationPlugin... plugins) {
        return new DurationConfig(List.of(plugins));
    }

    public static TimedResponse<?> measure(String operationName, Runnable runnable) {
        return getInstance().measure(operationName, runnable);
    }

    public static <R> TimedResponse<R> measure(String operationName, CheckedSupplier<R> function) {
        return getInstance().measure(operationName, function);
    }

    public static String calculateTimePerWord(Duration duration, int wordCount) {
        if (wordCount <= 0) {
            log.warn("Word count must be greater than 0.");
            return null;
        }

        double seconds = duration.toMillis() / 1000.0; // Convert to seconds
        double timePerWord = seconds / wordCount;

        return String.format("%.3f", timePerWord);
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {

        T get() throws Exception;

    }

    @FunctionalInterface
    public interface DurationPlugin {

        void onAction(DurationContext context);

    }

    public record TimedResponse<T>(Duration duration, T result) implements MarkdownSupport {

        public <U> Stream<U> stream() {
            if (result instanceof Collection<?> collection) {
                return (Stream<U>) collection.stream();
            }

            return Stream.of((U) result);
        }

        public <U> TimedResponse<U> mutate(Function<T, U> fn) {
            return new TimedResponse<>(duration, fn.apply(result));
        }

        @SneakyThrows
        public String stringResult() {
            var objectMapper = new ObjectMapper();
            if (result instanceof String str) {
                if (JavaUtil.isValidJson(str)) {
                    JsonNode node = objectMapper.readTree(str);
                    return objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(node);
                } else {
                    return formatMarkdown(str);
                }
            } else {
                return objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(result);
            }
        }

    }

    public record DurationContext(Map<String, Object> context, String operationName, TimedResponse<?> response) {
    }

    @RequiredArgsConstructor
    public static class ArgumentPlugin implements DurationPlugin {

        private final String name;
        private final Object argument;

        public static DurationPlugin forArgument(String name, Object argument) {
            return new ArgumentPlugin(name, argument);
        }

        public static DurationPlugin forArgument(Object argument) {
            return forArgument(ARGUMENT, argument);
        }

        @Override
        public void onAction(DurationContext context) {
            context.context().put(name, argument);
        }

    }

    @RequiredArgsConstructor
    public static final class DurationConfig {

        private final List<DurationPlugin> plugins;

        public static String humanReadableDuration(Duration duration) {
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            long millis = duration.toMillisPart();

            return String.format("%02d:%02d:%02d.%03d (hh:mm:ss.mmm)", hours, minutes, seconds, millis);
        }

        public TimedResponse<?> measure(String operationName, Runnable runnable) {
            return _measure(operationName, () -> {
                runnable.run();

                return null;
            });
        }

        public <R> TimedResponse<R> measure(String operationName, CheckedSupplier<R> function) {
            return _measure(operationName, () -> {
                try {
                    return function.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        public <R> TimedResponse<R> _measure(String operationName, Supplier<R> function) {
            log.debug("Started: {}", operationName);
            Instant start = Instant.now();
            R result = function.get();
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            String report = humanReadableDuration(duration);

            log.debug("Finished: {} took {}", operationName, report);

            var response = new TimedResponse<>(duration, result);
            var context = new DurationContext(new HashMap<>(), operationName, response);
            for (DurationPlugin plugin : plugins) {
                plugin.onAction(context);
            }

            return response;
        }

    }

}
