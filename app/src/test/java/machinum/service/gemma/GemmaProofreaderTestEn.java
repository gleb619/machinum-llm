package machinum.service.gemma;

import machinum.TestApplication;
import machinum.service.AbstractProofreaderTestEn;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=gemma2:27b"
                , "spring.ai.ollama.chat.options.temperature=0.3"
                , "spring.ai.ollama.chat.options.numCtx=8192"
        }
)
public class GemmaProofreaderTestEn extends AbstractProofreaderTestEn {

}
