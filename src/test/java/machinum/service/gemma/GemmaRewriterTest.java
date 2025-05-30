package machinum.service.gemma;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=gemma2:27b",
                "app.rewrite.numCtx=8192"
        }
)
public class GemmaRewriterTest extends AbstractRewriterTest {

}
