package machinum.service;

import machinum.TestApplication;
import machinum.extract.SummaryExtractor;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.util.DurationUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;

import static machinum.flow.FlowContextActions.text;
import static machinum.service.NormalTest.ReportInput.data;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=deepseek-r1:32b"
//                "spring.ai.ollama.chat.model=qwen2.5:3b"
                , "spring.ai.ollama.chat.options.temperature=0.3"
//                ,"spring.ai.ollama.chat.options.topK=30"
//                ,"spring.ai.ollama.chat.options.mirostatTau=3.0"
//                ,"spring.ai.ollama.chat.options.stop=nodata_nodata_nodata"
//                ,"spring.ai.ollama.chat.options.numCtx=30768"

                , "app.split.mode=balanced"
        }
)
class SummaryExtractorTest extends NormalTest {

    @Autowired
    SummaryExtractor summarizer;

    @Test
    void testSummarize() throws IOException {
        String chapterText = Files.readString(chapterPath);
        var summary = DurationUtil.measure("summary", () -> {
            return summarizer.extractSummary(FlowContextActions.of(text(chapterText)));
        }).mutate(FlowContext::context);

        withReport(data("summary", chapterText, summary), () -> {
            assertCharacterCount(summary.result(), (int) calculatePart(10, chapterText));
        });
    }

}
