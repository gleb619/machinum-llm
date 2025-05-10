package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
        }
)
public class TproRewriterTest extends AbstractRewriterTest {

}
