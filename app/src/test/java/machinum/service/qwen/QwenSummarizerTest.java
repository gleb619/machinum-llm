package machinum.service.qwen;

import machinum.TestApplication;
import machinum.service.AbstractSummarizerTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.summary.model=qwen2.5:32b"
                , "app.summary.temperature=0.3"
                , "app.summary.numCtx=30768"
        }
)
public class QwenSummarizerTest extends AbstractSummarizerTest {

}
