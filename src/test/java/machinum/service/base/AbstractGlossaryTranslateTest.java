package machinum.service.base;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.GlossaryJsonTranslate;
import machinum.extract.GlossaryTranslate;
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
import java.util.function.Function;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.jsonText;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public abstract class AbstractGlossaryTranslateTest extends NormalTest {

    @Autowired
    GlossaryJsonTranslate glossaryJsonTranslate;

    @Autowired
    GlossaryTranslate glossaryTranslate;

    @Disabled
    @RepeatedIfExceptionsTest
    @DisplayName("GlossaryJsonExtractor translate")
    void testCreateGlossaryViaJson() throws IOException {
        doTest(glossaryJsonTranslate::translateWithCache);
    }

    @RepeatedIfExceptionsTest
    @DisplayName("GlossaryJsonExtractor translate")
    void testCreateGlossaryViaProperties() throws IOException {
        doTest(glossaryTranslate::translate);
    }

    private void doTest(Function<FlowContext<Chapter>, FlowContext<Chapter>> fn) throws IOException {
        var cleanText = Files.readString(rewrittenChapterPath);
        var summaryText = Files.readString(summaryPath);
        var oldGlossaryJson = readJson(previousGlossaryPath, new TypeReference<List<ObjectName>>() {
        });
        var glossaryJson = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var glossary = DurationMeasureUtil.measure("glossaryTranslate", () -> {
            return fn.apply((FlowContext<Chapter>) of(
                    iteration(1),
                    text(cleanText),
                    context(summaryText),
                    glossary(glossaryJson),
                    glossary(oldGlossaryJson).asObsolete()
            ));
        }).mutate(FlowContext::glossary);

        withReport(jsonText("glossaryTranslate", glossary), () -> {
            assertCharacterCount(glossary.stringResult(), (int) calculatePart(10, cleanText));
        });
    }

}
