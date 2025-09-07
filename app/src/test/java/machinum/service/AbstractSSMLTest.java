package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import machinum.extract.SSMLConverter;
import machinum.flow.AppFlowActions;
import machinum.flow.FlowArgument;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.data;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public class AbstractSSMLTest extends NormalTest {

    @Autowired
    SSMLConverter converter;

    @Test
    void testProofreader() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var translatedText = Files.readString(translatedPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var ssml = DurationMeasureUtil.measure("proofread", () -> {
                    return converter.convert((FlowContext<Chapter>)
                            of(text(chapterText),
                                    iteration(1),
                                    translatedText(translatedText),
                                    context(contextText),
                                    AppFlowActions.glossary(glossary)));
                }).mutate(FlowContext::resultArg)
                .mutate(FlowArgument::stringValue);

        withReport(data("ssml", chapterText, ssml), () -> {
            assertCharacterCount(ssml.result(), (int) calculatePart(90, chapterText));
        });
    }

}
