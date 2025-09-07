package machinum.service.mistral;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=hf.co/bartowski/Mistral-Nemo-Instruct-2407-GGUF:F16"
        }
)
public class MistralNemoRewriterTest extends AbstractRewriterTest {

}
