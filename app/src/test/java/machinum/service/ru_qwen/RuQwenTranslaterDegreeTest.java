package machinum.service.ru_qwen;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
//                "app.translate.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q5_K_M"
                "app.translate.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q6_K"

                , "app.translate.temperature=0.8"
                , "app.translate.numCtx=11264"

                , "app.translate.scoring.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q6_K"
        }
)
public class RuQwenTranslaterDegreeTest extends AbstractTranslaterTest {
}
