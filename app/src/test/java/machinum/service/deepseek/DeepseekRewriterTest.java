package machinum.service.deepseek;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=deepseek-r1:32b"
        }
)
public class DeepseekRewriterTest extends AbstractRewriterTest {

}
