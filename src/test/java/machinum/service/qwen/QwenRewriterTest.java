package machinum.service.qwen;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=hf.co/lmstudio-community/Qwen2.5-14B-Instruct-GGUF:q8_0"
//                "app.rewrite.model=qwen2.5:32b"
        }
)
public class QwenRewriterTest extends AbstractRewriterTest {

}
