package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=owl/t-lite:instruct"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=8192"
        }
)
public class TliteGlossaryTranslateTest extends AbstractGlossaryTranslateTest {
}
