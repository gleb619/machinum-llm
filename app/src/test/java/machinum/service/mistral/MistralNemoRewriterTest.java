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
                "app.rewrite.model=hf.co/bartowski/Mistral-Nemo-Instruct-2407-GGUF:F16"
        }
)
public class MistralNemoRewriterTest extends AbstractRewriterTest {

}
