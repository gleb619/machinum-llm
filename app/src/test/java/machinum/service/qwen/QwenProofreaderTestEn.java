package machinum.service.qwen;

import machinum.TestApplication;
import machinum.service.AbstractProofreaderTestEn;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=qwen2.5:32b"
                , "spring.ai.ollama.chat.options.temperature=0.3"
                , "spring.ai.ollama.chat.options.numCtx=30768"
        }
)
public class QwenProofreaderTestEn extends AbstractProofreaderTestEn {

}
