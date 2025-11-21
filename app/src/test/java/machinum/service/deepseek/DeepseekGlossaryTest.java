package machinum.service.deepseek;

import machinum.TestApplication;
import machinum.extract.service.AbstractGlossaryTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=deepseek-r1:32b"
                , "app.glossary.extract.temperature=0.8"
                , "spring.ai.ollama.chat.options.numCtx=30768"
        }
)
public class DeepseekGlossaryTest extends AbstractGlossaryTest {

}
