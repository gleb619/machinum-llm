package machinum.service.mistral;

import machinum.TestApplication;
import machinum.service.AbstractGlossaryExtractorTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=hf.co/bartowski/Mistral-Nemo-Instruct-2407-GGUF:F16"
                , "spring.ai.ollama.chat.options.temperature=0.3"
                , "spring.ai.ollama.chat.options.numCtx=30768"
        }
)
public class MistralNemoGlossaryExtractorTest extends AbstractGlossaryExtractorTest {

}
