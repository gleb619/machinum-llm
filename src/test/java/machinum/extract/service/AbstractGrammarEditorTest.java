package machinum.extract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.GrammarEditor;
import machinum.extract.Translater;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.service.NormalTest;
import machinum.util.DurationUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static machinum.flow.FlowContext.*;
import static machinum.service.NormalTest.ReportInput.data;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public abstract class AbstractGrammarEditorTest extends NormalTest {

    @Autowired
    GrammarEditor grammarEditor;

    @Autowired
    Translater translater;

    @Disabled
    @DisplayName("Grammar edit")
    @RepeatedIfExceptionsTest
    void testTranslate() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var oldTranslatedText = Files.readString(previousTranslatedPath);
//        var translatedText = Files.readString(translatedPath);
        var translatedText = Files.readString(Path.of("target/texts/translate_example.txt"));
//        var translatedText = Files.readString(Path.of("target/texts/translate_new_2025-02-22T15:34:31.153360383.txt"));

        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var grammarEdit = DurationUtil.measure("grammarEdit", () -> {
            return grammarEditor.fixTranslate((FlowContext<Chapter>) of(
                    translatedText(oldTranslatedText).obsolete(),
                    translatedText(translatedText),
                    text(chapterText),
                    context(contextText),
                    glossary(glossary)
            ));
        }).mutate(FlowContext::translatedText);

        withReport(data("grammarEdit", translatedText, grammarEdit), () -> {
            assertCharacterCount(grammarEdit.stringResult(), (int) calculatePart(90, translatedText));
        });
    }

    @DisplayName("Grammar edit with scoring")
    @RepeatedIfExceptionsTest
    void testTranslateWithScoring() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var oldTranslatedText = Files.readString(previousTranslatedPath);
//        var translatedText = Files.readString(translatedPath);
        var translatedText = Files.readString(Path.of("target/texts/translate_example.txt"));
//        var translatedText = Files.readString(Path.of("target/texts/translate_new_2025-02-16T16:17:45.378848324.txt"));
//        var translatedText = Files.readString(Path.of("target/texts/translate_new_2025-02-22T15:34:31.153360383.txt"));

        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var grammarEdit = DurationUtil.measure("grammarEdit", () -> {
            return translater.fixGrammarWithScoringLoop((FlowContext<Chapter>) of(
                    translatedText(oldTranslatedText).obsolete(),
                    translatedText(translatedText),
                    text(chapterText),
                    context(contextText),
                    glossary(glossary)
            ));
        }).mutate(FlowContext::translatedText);

        withReport(data("grammarEdit", translatedText, grammarEdit), () -> {
            assertCharacterCount(grammarEdit.stringResult(), (int) calculatePart(70, translatedText));
        });
    }

}
