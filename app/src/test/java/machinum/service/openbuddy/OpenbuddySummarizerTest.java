package machinum.service.openbuddy;

import machinum.TestApplication;
import machinum.service.AbstractSummarizerTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=hf.co/OpenBuddy/openbuddy-qwq-32b-v24.1q-gguf:latest"
                , "spring.ai.ollama.chat.options.temperature=0.3"
                , "spring.ai.ollama.chat.options.numCtx=30768"
        }
)
public class OpenbuddySummarizerTest extends AbstractSummarizerTest {

}
