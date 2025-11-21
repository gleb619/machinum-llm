package machinum.service.deepseek;

import machinum.TestApplication;
import machinum.extract.service.AbstractGrammarEditorTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.copy-editing.model=hf.co/bartowski/DeepSeek-R1-Distill-Qwen-32B-GGUF:Q5_K_M"
                , "app.translate.copy-editing.temperature=0.3"

                , "app.translate.copy-editing-scoring.model=hf.co/bartowski/DeepSeek-R1-Distill-Qwen-32B-GGUF:Q5_K_M"
        }
)
public class DeepseekDGrammarEditorTest extends AbstractGrammarEditorTest {
}
