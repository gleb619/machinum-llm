package machinum.service.deepseek;

import machinum.TestApplication;
import machinum.extract.service.AbstractGrammarEditorTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.copy-editing.model=hf.co/bartowski/DeepSeek-R1-Distill-Qwen-32B-GGUF:Q5_K_M"
                , "app.translate.copy-editing.temperature=0.8"

                , "app.translate.copy-editing-scoring.model=hf.co/bartowski/DeepSeek-R1-Distill-Qwen-32B-GGUF:Q5_K_M"
        }
)
public class DeepseekDGrammarEditorDegreeTest extends AbstractGrammarEditorTest {
}
