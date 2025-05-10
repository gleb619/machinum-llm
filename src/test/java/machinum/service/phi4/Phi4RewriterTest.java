package machinum.service.phi4;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=phi4:14b-fp16"
        }
)
public class Phi4RewriterTest extends AbstractRewriterTest {

}
