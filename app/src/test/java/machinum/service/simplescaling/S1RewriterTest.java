package machinum.service.simplescaling;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Doesn't fit, 32GB model
 */
@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=hf.co/bartowski/simplescaling_s1-32B-GGUF:Q6_K_L"
        }
)
public class S1RewriterTest extends AbstractRewriterTest {

}
