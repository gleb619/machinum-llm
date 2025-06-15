package machinum.extract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.Glossary;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.service.DbTest;
import machinum.util.DurationUtil;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.jsonText;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

public abstract class AbstractGlossaryTest extends DbTest {

    @Autowired
    Glossary glossary;

    @DisplayName("Glossary")
    @RepeatedIfExceptionsTest
    void testGlossary() throws IOException {
        String previousContext = Files.readString(previousSummaryPath);
        var previousGlossary = readJson(previousGlossaryPath, new TypeReference<List<ObjectName>>() {
        });

        String chapterText = Files.readString(rewrittenChapterPath);
        String contextText = Files.readString(summaryPath);

        //TODO add to FlowContext a Chapter#number & consolidatedGlossary
        var glossary = DurationUtil.measure("glossaryExtractor", () -> {
            return this.glossary.extractGlossary((FlowContext<Chapter>) of(
                    context(previousContext).asObsolete(),
                            glossary(previousGlossary),
                            text(chapterText),
                            context(contextText)
                    )
            );
        }).mutate(FlowContext::glossary);

        withReport(jsonText("glossaryExtractor", glossary), () -> {
            assertCharacterCount(glossary.stringResult(), (int) calculatePart(10, chapterText));
        });
    }

}
