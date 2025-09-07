package machinum.service.gemma;

import machinum.TestApplication;
import machinum.extract.service.AbstractGlossaryTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.glossary.extract.model=hf.co/lmstudio-community/gemma-3-27b-it-GGUF:Q6_K"
                , "app.glossary.extract.temperature=0.8"
                , "app.glossary.extract.numCtx=10240"
        }
)
public class Gemma3GlossaryDegreeTest extends AbstractGlossaryTest {

}
