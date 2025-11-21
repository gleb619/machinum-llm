package machinum.service.gigachat;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=infidelis/GigaChat-20B-A3B-instruct-v1.5:q8_0"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=8192"
        }
)
public class GigaChatTranslateTest extends AbstractGlossaryTranslateTest {
}
