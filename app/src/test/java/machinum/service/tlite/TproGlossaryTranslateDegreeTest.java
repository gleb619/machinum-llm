package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.glossary.translate.model=hf.co/t-tech/T-pro-it-1.0-Q6_K-GGUF:latest"
                , "app.glossary.translate.temperature=0.8"
                , "app.glossary.translate.numCtx=8192"
//                "app.glossary.translate.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
//                ,"app.glossary.translate.temperature=0.8"
//                ,"app.glossary.translate.numCtx=16384"
        }
)
public class TproGlossaryTranslateDegreeTest extends AbstractGlossaryTranslateTest {
}
