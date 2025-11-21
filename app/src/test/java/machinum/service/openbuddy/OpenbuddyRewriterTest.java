package machinum.service.openbuddy;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=hf.co/OpenBuddy/openbuddy-qwq-32b-v24.1q-gguf:latest"
        }
)
public class OpenbuddyRewriterTest extends AbstractRewriterTest {

}
