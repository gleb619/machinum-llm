package machinum.service;

import machinum.TestApplication;
import machinum.extract.CoT;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;

import static machinum.flow.FlowContextActions.context;
import static machinum.flow.FlowContextActions.text;
import static machinum.service.NormalTest.ReportInput.onlyNewText;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.transform.options.model=deepseek-r1:32b"
//                 "spring.ai.ollama.transform.options.model=qwen2.5:3b"
                , "spring.ai.ollama.transform.options.temperature=0.3"
                , "spring.ai.ollama.transform.options.topK=30"
                , "spring.ai.ollama.transform.options.mirostatTau=2.0"
                , "spring.ai.ollama.transform.options.stop=nodata_nodata_nodata"
                , "spring.ai.ollama.transform.options.numCtx=30768"
                , "spring.ai.ollama.transform.options.format=json"

                , "app.split.mode=balanced"
        }
)
class CoTTest extends NormalTest {

    @Autowired
    CoT coT;

    @Test
    void testCreateCoT() throws IOException {
        var chapterText = Files.readString(chapterPath);
        var contextText = Files.readString(contextPath);

        var cot = DurationMeasureUtil.measure("CoT", () -> {
            return coT.createCoT((FlowContext<Chapter>) FlowContextActions.of(
                    text(chapterText),
                    context(chapterText)
            ));
        });

        withReport(onlyNewText("cot", cot), () -> {
            assertCharacterCount(cot.stringResult(), (int) calculatePart(85, chapterText));
        });
    }

}
