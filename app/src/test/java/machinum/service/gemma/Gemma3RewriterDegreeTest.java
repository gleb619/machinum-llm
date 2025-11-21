package machinum.service.gemma;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=hf.co/lmstudio-community/gemma-3-27b-it-GGUF:Q6_K"
                , "app.rewrite.temperature=0.8"
                , "app.rewrite.numCtx=10240"
        }
)
public class Gemma3RewriterDegreeTest extends AbstractRewriterTest {

}
