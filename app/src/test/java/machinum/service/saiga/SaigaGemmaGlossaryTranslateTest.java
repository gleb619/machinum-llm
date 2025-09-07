package machinum.service.saiga;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.glossary.translate.model=hf.co/mradermacher/saiga_gemma2_10b-GGUF:F16"
                , "app.glossary.translate.numCtx=8192"
        }
)
public class SaigaGemmaGlossaryTranslateTest extends AbstractGlossaryTranslateTest {
}
