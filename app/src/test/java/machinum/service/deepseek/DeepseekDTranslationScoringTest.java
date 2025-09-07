package machinum.service.deepseek;

import machinum.TestApplication;
import machinum.extract.service.AbstractTranslationScoringTest;
import org.springframework.boot.test.context.SpringBootTest;

@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.scoring.model=hf.co/bartowski/DeepSeek-R1-Distill-Qwen-32B-GGUF:Q5_K_M"
                , "app.translate.scoring.temperature=0.3"
        }
)
public class DeepseekDTranslationScoringTest extends AbstractTranslationScoringTest {
}
