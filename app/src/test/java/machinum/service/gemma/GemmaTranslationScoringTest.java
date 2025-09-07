package machinum.service.gemma;

import machinum.TestApplication;
import machinum.extract.service.AbstractTranslationScoringTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.scoring.model=gemma2:27b"
                , "app.translate.scoring.temperature=0.3"
                , "app.translate.scoring.numCtx=8192"
        }
)
public class GemmaTranslationScoringTest extends AbstractTranslationScoringTest {
}
