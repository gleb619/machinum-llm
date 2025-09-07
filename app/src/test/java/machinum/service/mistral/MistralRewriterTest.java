package machinum.service.mistral;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=mistral:7b-instruct-v0.3-fp16"
        }
)
public class MistralRewriterTest extends AbstractRewriterTest {

}
