package machinum.service.saiga;

import machinum.TestApplication;
import machinum.extract.service.AbstractTranslationScoringTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.scoring.model=hf.co/IlyaGusev/saiga_nemo_12b_gguf:BF16"
                , "app.translate.scoring.temperature=0.3"
                , "app.translate.scoring.numCtx=8192"
        }
)
public class SaigaNemoTranslationScoringTest extends AbstractTranslationScoringTest {
}
