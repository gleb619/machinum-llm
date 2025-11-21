package machinum.service.saiga;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.glossary.translate.model=hf.co/mradermacher/saiga_gemma2_10b-GGUF:F16"
                , "app.glossary.translate.numCtx=8192"
                , "app.glossary.translate.temperature=0.8"
        }
)
public class SaigaGemmaGlossaryTranslateDegreeTest extends AbstractGlossaryTranslateTest {
}
