package machinum.service.vikhr;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.springframework.boot.test.context.SpringBootTest;

@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=hf.co/Vikhrmodels/Vikhr-Gemma-2B-instruct-GGUF:F32"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=8192"
        }
)
public class Vikhr2TranslateTest extends AbstractGlossaryTranslateTest {
}
