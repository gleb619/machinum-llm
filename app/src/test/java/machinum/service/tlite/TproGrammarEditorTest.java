package machinum.service.tlite;

import machinum.TestApplication;
import machinum.extract.service.AbstractGrammarEditorTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.copy-editing.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
                , "app.translate.copy-editing.temperature=0.3"

                , "app.translate.copy-editing-scoring.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
        }
)
public class TproGrammarEditorTest extends AbstractGrammarEditorTest {
}
