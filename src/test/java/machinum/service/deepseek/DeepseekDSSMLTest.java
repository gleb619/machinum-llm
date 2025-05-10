package machinum.service.deepseek;

import machinum.TestApplication;
import machinum.service.AbstractSSMLTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.ssml.model=hf.co/lmstudio-community/DeepSeek-R1-Distill-Qwen-14B-GGUF:q8_0",
                "app.ssml.temperature=0.7",
                "app.ssml.numCtx=10240"
        }
)
public class DeepseekDSSMLTest extends AbstractSSMLTest {
}
