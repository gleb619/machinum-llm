package machinum.service.openchat;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=openchat:7b-v3.5-0106-fp16"
                , "app.translate.temperature=0.3"
        }
)
public class OpenchatTranslaterTest extends AbstractTranslaterTest {
}
