package machinum.service.vikhr;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=hf.co/Vikhrmodels/QVikhr-2.5-1.5B-Instruct-SMPO_GGUF:q8_0"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=16384"
        }
)
public class Vikhr1TranslaterTest extends AbstractTranslaterTest {
}
