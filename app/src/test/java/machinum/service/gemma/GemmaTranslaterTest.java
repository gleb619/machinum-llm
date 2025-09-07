package machinum.service.gemma;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=gemma2:27b"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=16384"
        }
)
public class GemmaTranslaterTest extends AbstractTranslaterTest {
}
