package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.Summarizer;
import machinum.flow.AppFlowActions;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.data;
import static machinum.service.NormalTest.ReportInput.onlyNewText;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public abstract class AbstractSummarizerTest extends NormalTest {

    @Autowired
    Summarizer summarizer;

    @Disabled
    @DisplayName("Summarize")
    @RepeatedIfExceptionsTest
    void testSummarize() throws IOException {
        String chapterText = Files.readString(rewrittenChapterPath);
        var summary = DurationMeasureUtil.measure("summary", () -> {
            return summarizer.summarize(FlowContextActions.of(text(chapterText)));
        }).mutate(FlowContext::context);

        withReport(data("summary", chapterText, summary), () -> {
            assertCharacterCount(summary.result(), (int) calculatePart(10, chapterText));
        });
    }

    @RepeatedIfExceptionsTest
    @DisplayName("Summarize with context")
    void testSummarizeWithPreviousContext() throws IOException {
        String chapterText = Files.readString(rewrittenChapterPath);
        String contextText = Files.readString(previousSummaryPath);
        var glossary = readJson(previousGlossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var summary = DurationMeasureUtil.measure("summary", () -> {
            return summarizer.summarize((FlowContext<Chapter>) of(
                    text(chapterText),
                    context(contextText),
                    AppFlowActions.glossary(glossary)
            ));
        }).mutate(FlowContext::context);

        withReport(onlyNewText("summary", summary), () -> {
            assertCharacterCount(summary.result(), (int) calculatePart(5, chapterText));
        });
    }

}
