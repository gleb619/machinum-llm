package machinum.extract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.TranslationScoring;
import machinum.flow.AppFlowActions;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.service.NormalTest;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static machinum.config.Constants.SCORE;
import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.jsonText;
import static machinum.util.TextProcessingTestUtil.assertLineCount;

public abstract class AbstractTranslationScoringTest extends NormalTest {

    @Autowired
    TranslationScoring translationScoring;

    @DisplayName("Translation score")
    @RepeatedIfExceptionsTest
    void testTranslate() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var oldTranslatedText = Files.readString(previousTranslatedPath);
        var translatedText = Files.readString(translatedPath);
//        var translatedText = Files.readString(Path.of("build/texts/translate_new_2025-02-16T16:17:45.378848324.txt"));
//        var translatedText = Files.readString(Path.of("build/texts/translate_new_2025-02-22T15:34:31.153360383.txt"));

        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var translationScore = DurationMeasureUtil.measure("translationScore", () -> {
            return translationScoring.scoreTranslate((FlowContext<Chapter>) of(
                    translatedText(oldTranslatedText).asObsolete(),
                    translatedText(translatedText),
                    text(chapterText),
                    context(contextText),
                    AppFlowActions.glossary(glossary)
            ));
        }).mutate(ctx -> ctx.arg(SCORE).stringValue());

        withReport(jsonText("translationScore", translationScore), () -> {
            assertLineCount(translationScore.stringResult(), 1);
        });
    }

}
