package machinum.service;

import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.GlossaryExtractor;
import machinum.flow.AppFlowActions;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.jsonText;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public abstract class AbstractGlossaryExtractorTest extends NormalTest {

    @Autowired
    GlossaryExtractor glossaryExtractor;

    @DisplayName("Glossary")
    @RepeatedIfExceptionsTest
    void testCreateGlossary() throws IOException {
        String chapterText = Files.readString(rewrittenChapterPath);
        String contextText = Files.readString(summaryPath);

        var glossary = DurationMeasureUtil.measure("glossaryExtractor", () -> {
            return this.glossaryExtractor.firstExtract((FlowContext<Chapter>) of(text(chapterText), context(contextText)));
        }).mutate(AppFlowActions::glossary);

        withReport(jsonText("glossaryExtractor", glossary), () -> {
            assertCharacterCount(glossary.stringResult(), (int) calculatePart(10, chapterText));
        });
    }

}
