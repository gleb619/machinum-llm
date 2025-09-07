package machinum.service.simplescaling;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Doesn't fit, 32GB model
 */
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
