package machinum.service.base;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.Translater;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.service.NormalTest;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.data;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public abstract class AbstractTranslaterTest extends NormalTest {

    @Autowired
    Translater translater;

    //    @Disabled
    @DisplayName("Translate")
    @RepeatedIfExceptionsTest
    void testTranslate() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var translate = DurationMeasureUtil.measure("translate", () -> {
            return translater.translateAll((FlowContext<Chapter>) of(
                    text(chapterText),
                    context(contextText),
                    glossary(glossary)
            ));
        }).mutate(FlowContext::translatedText);

        withReport(data("translate", chapterText, translate), () -> {
            assertCharacterCount(translate.stringResult(), (int) calculatePart(65, chapterText));
        });
    }

    @Disabled
    @DisplayName("Translate with scoring")
    @RepeatedIfExceptionsTest
    void testTranslateWithScoringLoop() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var translate = DurationMeasureUtil.measure("translate", () -> {
            return translater.translateWithScoringLoop((FlowContext<Chapter>) of(
                    text(chapterText),
                    context(contextText),
                    glossary(glossary)
            ));
        }).mutate(FlowContext::translatedText);

        withReport(data("translate", chapterText, translate), () -> {
            assertCharacterCount(translate.stringResult(), (int) calculatePart(65, chapterText));
        });
    }

    @Disabled
    @DisplayName("Translate with scoring")
    @RepeatedIfExceptionsTest
    void testTranslateWithScoring() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var translate = DurationMeasureUtil.measure("translate", () -> {
            return translater.translateWithScoring((FlowContext<Chapter>) of(
                    iteration(1),
                    text(chapterText),
                    context(contextText),
                    glossary(glossary)
            ));
        }).mutate(FlowContext::translatedText);

        withReport(data("translate", chapterText, translate), () -> {
            assertCharacterCount(translate.stringResult(), (int) calculatePart(65, chapterText));
        });
    }

    @Disabled
    @DisplayName("Score and fix")
    @RepeatedIfExceptionsTest
    void testScoreAndFix() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var translatedText = Files.readString(translatedPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var translate = DurationMeasureUtil.measure("translate", () -> {
            return translater.scoreAndFix((FlowContext<Chapter>) of(
                    iteration(1),
                    text(chapterText),
                    translatedText(translatedText),
                    context(contextText),
                    glossary(glossary)
            ));
        }).mutate(FlowContext::translatedText);

        withReport(data("translate", chapterText, translate), () -> {
            assertCharacterCount(translate.stringResult(), (int) calculatePart(65, chapterText));
        });
    }

}
