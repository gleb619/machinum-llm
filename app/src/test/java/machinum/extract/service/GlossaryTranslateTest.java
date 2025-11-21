package machinum.extract.service;

import com.fasterxml.jackson.core.type.TypeReference;
import machinum.TestApplication;
import machinum.extract.GlossaryJsonTranslate;
import machinum.flow.AppFlowActions;
import machinum.flow.model.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.service.NormalTest;
import machinum.util.DurationMeasureUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static machinum.flow.model.helper.FlowContextActions.*;
import static machinum.service.NormalTest.ReportInput.jsonText;
import static machinum.util.JavaUtil.calculatePart;
import static machinum.util.TextProcessingTestUtil.assertCharacterCount;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=deepseek-r1:32b"
//                "spring.ai.ollama.chat.model=qwen2.5:3b"
                , "spring.ai.ollama.chat.options.temperature=0.8"
//                ,"spring.ai.ollama.chat.options.topK=30"
//                ,"spring.ai.ollama.chat.options.mirostatTau=3.0"
                , "spring.ai.ollama.chat.options.stop=nodata_nodata_nodata"
//                ,"spring.ai.ollama.chat.options.numCtx=8192"

                , "spring.ai.ollama.transform.options.model=deepseek-r1:32b"
//                ,"spring.ai.ollama.transform.options.model=qwen2.5:3b"
                , "spring.ai.ollama.transform.options.temperature=0.8"
//                ,"spring.ai.ollama.transform.options.topK=30"
//                ,"spring.ai.ollama.transform.options.mirostatTau=2.0"
                , "spring.ai.ollama.transform.options.stop=nodata_nodata_nodata"
//                ,"spring.ai.ollama.transform.options.numCtx=30768"
                , "spring.ai.ollama.transform.options.format=json"

                , "app.split.mode=balanced"
        }
)
class GlossaryTranslateTest extends NormalTest {

    @Autowired
    GlossaryJsonTranslate glossaryTranslate;

    @Test
    void testTranslate() throws IOException {
        var cleanText = Files.readString(rewrittenChapterPath);
        var summaryText = Files.readString(summaryPath);
        var glossaryJson = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });

        var glossary = DurationMeasureUtil.measure("glossaryTranslate", () -> {
            return glossaryTranslate.translateWithCache((FlowContext<Chapter>) of(text(cleanText), context(summaryText), AppFlowActions.glossary(glossaryJson)));
        }).mutate(AppFlowActions::glossary);

        withReport(jsonText("glossaryTranslate", glossary), () -> {
            assertCharacterCount(glossary.stringResult(), (int) calculatePart(10, cleanText));
        });
    }

}
