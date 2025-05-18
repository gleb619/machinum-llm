package machinum.service;

import io.github.artsok.RepeatedIfExceptionsTest;
import machinum.extract.Rewriter;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.util.DurationUtil;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;

import static machinum.flow.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.data;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;
import static machinum.util.TextProcessingTestUtil.assertSimilarity;

public abstract class AbstractRewriterTest extends NormalTest {

    @Autowired
    Rewriter rewriter;

    @DisplayName("Rewrite")
    @RepeatedIfExceptionsTest
    void testRewrite() throws IOException {
        String chapterText = Files.readString(chapterPath);
        String oldChapterText = Files.readString(previousRewrittenChapterPath);

        var rewrite = DurationUtil.measure("rewrite", () -> {
            return rewriter.rewrite((FlowContext<Chapter>) of(
                    iteration(1),
                    text(oldChapterText).obsolete(),
                    text(chapterText)
            ));
        }).mutate(FlowContext::text);

        withReport(data("rewriter", chapterText, rewrite), () -> {
            assertCharacterCount(rewrite.result(), (int) calculatePart(70, chapterText));
            assertSimilarity(rewrite.result(), chapterText, 0.8);
        });
    }

}
