package machinum.service.sainemo;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=hf.co/mradermacher/SAINEMO-reMIX-GGUF:q8_0"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=16384"
        }
)
public class SainemoTranslaterTest extends AbstractTranslaterTest {
}
