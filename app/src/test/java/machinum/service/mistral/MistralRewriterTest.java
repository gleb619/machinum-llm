package machinum.service.mistral;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=mistral:7b-instruct-v0.3-fp16"
        }
)
public class MistralRewriterTest extends AbstractRewriterTest {

}
