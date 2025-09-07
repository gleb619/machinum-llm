package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=owl/t-lite:instruct"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=16384"
        }
)
public class TliteTranslaterTest extends AbstractTranslaterTest {
}
