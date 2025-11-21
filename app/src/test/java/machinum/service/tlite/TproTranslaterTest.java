package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
//                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q8_0-GGUF:latest"
                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q6_K-GGUF:latest"
//                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q5_K_M-GGUF:latest"
//                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"

                , "app.translate.temperature=0.3"
                , "app.translate.numCtx=11264"
//                ,"app.translate.numCtx=16384"

                , "app.translate.scoring.model=hf.co/t-tech/T-pro-it-1.0-Q5_K_M-GGUF:latest"

        }
)
public class TproTranslaterTest extends AbstractTranslaterTest {
}
