package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.AbstractSummarizerTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"
                , "spring.ai.ollama.chat.options.temperature=0.3"
                , "spring.ai.ollama.chat.options.numCtx=30768"
        }
)
public class TproSummarizerTest extends AbstractSummarizerTest {

}
