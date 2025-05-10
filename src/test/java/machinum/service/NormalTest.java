package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import lombok.SneakyThrows;
import machinum.util.DurationUtil;
import machinum.util.TextReportUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static machinum.processor.core.AssistantClient.State.state;
import static machinum.util.DurationUtil.DurationConfig.humanReadableDuration;

@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class NormalTest {

    public static final String FORMAT = "format";
    public static final String JSON_FORMAT = "json";
    public static final AtomicLong series = new AtomicLong();
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("[*_~`\\[\\](){}#+.!>\\|-]");
    protected Path rewrittenChapterPath;
    protected Path chapterPath;
    protected Path contextPath;
    protected Path glossaryPath;
    protected Path summaryPath;
    protected Path translatedPath;
    protected Path previousRewrittenChapterPath;
    protected Path previousGlossaryPath;
    protected Path previousSummaryPath;
    protected Path previousTranslatedPath;
    @Autowired
    DiffRowGenerator diffRowGenerator;
    @Value("${spring.ai.ollama.chat.options.topK:40}")
    String topK;
    @Value("${spring.ai.ollama.chat.options.mirostatTau:5.0}")
    String mirostatTau;

    @BeforeAll
    static void beforeAll() {
        var value = series.get();
        if (value == 0L) {
            series.getAndSet(System.currentTimeMillis());
        }
    }

    protected static String diffToString(List<DiffRow> rows) {
        StringBuilder builder = new StringBuilder();

        builder.append("|Original|New|  \n");
        builder.append("|--------|---|  \n");
        diffToMarkdown(rows, builder);

        return builder.toString();
    }

    protected static void diffToMarkdown(List<DiffRow> rows, StringBuilder builder) {
        for (DiffRow row : rows) {
            String oldOne = row.getOldLine();
            String newOne = row.getNewLine();

            if (!oldOne.isEmpty() || !newOne.isEmpty()) {
                builder.append("|" + oldOne + "|" + newOne + "|  \n");
            }
        }
    }

    protected static List<String> toLines(String chapterText) {
        return chapterText.lines()
                .map(String::trim)
                .map(NormalTest::escapeMarkdown)
                .toList();
    }

    protected static String escapeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return MARKDOWN_PATTERN.matcher(text).replaceAll("");
    }

    @BeforeEach
    void setUp() {
        previousRewrittenChapterPath = Path.of("src/test/resources/chapter01/rewrited_chapter_01.md");
        previousGlossaryPath = Path.of("src/test/resources/chapter01/glossary_chapter_01.json");
        previousSummaryPath = Path.of("src/test/resources/chapter01/summary_chapter_01.md");
        previousTranslatedPath = Path.of("src/test/resources/chapter01/translated_chapter_01.md");

        contextPath = Path.of("src/test/resources/chapter02/context_chapter_02.md");
        chapterPath = Path.of("src/test/resources/chapter02/origin_chapter_02.md");
        rewrittenChapterPath = Path.of("src/test/resources/chapter02/rewrited_chapter_02.md");
        glossaryPath = Path.of("src/test/resources/chapter02/glossary_chapter_02.json");
        summaryPath = Path.of("src/test/resources/chapter02/summary_chapter_02.md");
        translatedPath = Path.of("src/test/resources/chapter02/translated_chapter_02.md");
        Path.of("target/texts").toFile().mkdirs();
    }

    protected void withReport(ReportInput input, Runnable runnable) {
        try {
            var paths = persist(input.prefix(), input.oldText(), input.newText());

            var builder = createReport(input, paths[0], paths[1]);
            persistReport(input.prefix(), builder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        runnable.run();
    }

    @SneakyThrows
    protected <U> U readJson(Path path, TypeReference<U> typeReference) {
        return readJson(Files.readString(path), typeReference);
    }

    @SneakyThrows
    protected <U> U readJson(String text, TypeReference<U> typeReference) {
        return new ObjectMapper().readValue(text, typeReference);
    }

    protected String createReport(ReportInput input, Path chapterPath, Path targetPath) {
        String oldText = input.oldText();
        String newText = input.newText();

        var builder = new StringBuilder("# Revision  \n")
                .append("  \n")
                .append("## Info  \n")
                .append("  \n")
                .append("|Key|Value|  \n")
                .append("|--------|---|  \n")
                .append("|Name|").append(input.name()).append("|  \n")
                .append("|Type|").append(input.prefix()).append("|  \n")
                .append("|Chat model|").append(state().get().getModel()).append("|  \n")
                .append("|Temperature|").append(state().get().getTemperature()).append("|  \n")
                .append("|Top-k|").append(topK).append("|  \n")
                .append("|Mirostat tau|").append(mirostatTau).append("|  \n")
                .append("|Old text|[Link](file://").append(chapterPath.toAbsolutePath()).append(")|  \n")
                .append("|New text|[Link](file://").append(targetPath.toAbsolutePath()).append(")|  \n")
                .append("|Execution Time|").append(humanReadableDuration(input.duration())).append("|  \n")
                .append("|Date|").append(LocalDateTime.now()).append("|  \n");

        var seriesValue = series.get();
        if (seriesValue > 0) {
            builder
                    .append("|Series|").append(seriesValue).append("|  \n");
        }

        for (var metadata : input.metadata().entrySet()) {
            builder
                    .append("|%s|%s|  \n".formatted(metadata.getKey(), metadata.getValue()));
        }

        if (!oldText.isEmpty()) {
            var rows = diffRowGenerator.generateDiffRows(toLines(oldText), toLines(newText));
            var chapterReport = TextReportUtil.analyzeText(oldText);
            var targetReport = TextReportUtil.analyzeText(newText);

            builder
                    .append("  \n")
                    .append("## Report collate  \n")
                    .append("  \n")
                    .append(chapterReport.compare(targetReport))
                    .append("  \n")
                    .append("## Comparison  \n")
                    .append("  \n");

            builder.append("|Original|New|  \n");
            builder.append("|--------|---|  \n");
            diffToMarkdown(rows, builder);
        } else {
            var targetReport = TextReportUtil.analyzeText(newText);
            String targetText;

            if (JSON_FORMAT.equals(input.metadata().getOrDefault(FORMAT, ""))) {
                targetText = """
                        ```json
                        %s
                        ```
                        """.formatted(newText);
            } else {
                targetText = newText;
            }

            builder.append("  \n")
                    .append("## Report  \n")
                    .append("  \n")
                    .append(targetReport.toMarkdown())
                    .append("  \n")
                    .append("## Text  \n")
                    .append("  \n")
                    .append(targetText);
        }

        builder.append("  \n");

        return builder.toString();
    }

    protected void persistReport(String prefix, String reportText) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        Path reportPath = Path.of("target/texts", String.format("%s_report_%s.md".formatted(prefix, now)));
        Files.writeString(reportPath,
                reportText,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.printf("NormalTest.persistReport: \n\treport: file://%s%n", reportPath.toAbsolutePath());
    }

    protected Path[] persist(String prefix, String oldText, String newText) throws IOException {
        var now = LocalDateTime.now();
        var oldPath = Path.of("target/texts", String.format("%s_old_%s.txt".formatted(prefix, now)));
        var newPath = Path.of("target/texts", String.format("%s_new_%s.txt".formatted(prefix, now)));

        Files.writeString(oldPath,
                oldText,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        Files.writeString(newPath,
                newText,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.printf("NormalTest.persist: \n\told: file://%s\n\tnew: file://%s%n", oldPath.toAbsolutePath(), newPath.toAbsolutePath());

        return new Path[]{oldPath, newPath};
    }

    public record ReportInput(String prefix, String oldText, String newText, Duration duration, String name,
                              Map<String, Object> metadata) {

        public static ReportInput data(String prefix, String oldText, String newText) {
            return new ReportInput(prefix, oldText, newText, Duration.ZERO, "Test Report", Map.of());
        }

        public static ReportInput data(String prefix, String oldText, DurationUtil.TimedResponse<String> response) {
            return data(prefix, oldText, response, Map.of());
        }

        public static ReportInput data(String prefix, String oldText, DurationUtil.TimedResponse<String> response,
                                       Map<String, Object> metadata) {
            return new ReportInput(prefix, oldText, response.result(), response.duration(), "Test Report", metadata);
        }

        public static <T> ReportInput onlyNewText(String prefix, DurationUtil.TimedResponse<T> response) {
            return new ReportInput(prefix, "", response.stringResult(), response.duration(), "Test Report", Map.of());
        }

        public static <T> ReportInput jsonText(String prefix, DurationUtil.TimedResponse<T> response) {
            return new ReportInput(prefix, "", response.stringResult(), response.duration(), "Test Report", Map.of(
                    FORMAT, JSON_FORMAT
            ));
        }

    }

}
