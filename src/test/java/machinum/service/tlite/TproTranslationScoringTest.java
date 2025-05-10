package machinum.service.tlite;

import machinum.TestApplication;
import machinum.extract.service.AbstractTranslationScoringTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.scoring.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
                , "app.translate.scoring.temperature=0.3"
        }
)
public class TproTranslationScoringTest extends AbstractTranslationScoringTest {
}
