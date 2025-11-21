package machinum.service.saiga;

import machinum.TestApplication;
import machinum.extract.service.AbstractGrammarEditorTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.copy-editing.model=hf.co/mradermacher/saiga_gemma2_10b-GGUF:F16"
                , "app.translate.copy-editing.temperature=0.3"
                , "app.translate.copy-editing.numCtx=8192"

                , "app.translate.copy-editing-scoring.model=hf.co/mradermacher/saiga_gemma2_10b-GGUF:F16"
                , "app.translate.copy-editing-scoring.numCtx=8192"
        }
)
public class SaigaGemmaGrammarEditorTest extends AbstractGrammarEditorTest {
}
