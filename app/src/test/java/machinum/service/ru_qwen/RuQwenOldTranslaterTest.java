package machinum.service.ru_qwen;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=hf.co/RefalMachine/RuadaptQwen2.5-14B-R1-distill-preview-v1-GGUF:q8_0"
                , "spring.ai.ollama.chat.options.temperature=0.6"
                , "spring.ai.ollama.chat.options.numCtx=16384"
        }
)
public class RuQwenOldTranslaterTest extends AbstractTranslaterTest {
}
