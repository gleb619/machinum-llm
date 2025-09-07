package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.glossary.translate.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
                , "app.glossary.translate.temperature=0.3"
                , "app.glossary.translate.numCtx=16384"
        }
)
public class TproGlossaryTranslate3Test extends AbstractGlossaryTranslateTest {
}
