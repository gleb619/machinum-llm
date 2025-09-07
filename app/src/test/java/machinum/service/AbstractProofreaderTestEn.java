package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import machinum.extract.ProofreaderEnXml;
import machinum.flow.AppFlowActions;
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

public class AbstractProofreaderTestEn extends NormalTest {

    @Autowired
    ProofreaderEnXml proofreaderEn;

    @Test
    void testProofreader() throws IOException {
        var chapterText = Files.readString(rewrittenChapterPath);
        var contextText = Files.readString(summaryPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var proofread = DurationMeasureUtil.measure("proofread", () -> {
            return proofreaderEn.proofread((FlowContext<Chapter>)
                    of(text(chapterText),
                            context(contextText),
                            AppFlowActions.glossary(glossary)));
        }).mutate(FlowContext::proofread);

        withReport(data("proofread", chapterText, proofread), () -> {
            assertCharacterCount(proofread.result(), (int) calculatePart(90, chapterText));
        });
    }

}
