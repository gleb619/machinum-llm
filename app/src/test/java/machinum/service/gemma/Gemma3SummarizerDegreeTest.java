package machinum.service.gemma;

import machinum.TestApplication;
import machinum.service.AbstractSummarizerTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.summary.model=hf.co/lmstudio-community/gemma-3-27b-it-GGUF:Q6_K"
                , "app.summary.temperature=0.3"
                , "app.summary.numCtx=10240"
        }
)
public class Gemma3SummarizerDegreeTest extends AbstractSummarizerTest {

}
