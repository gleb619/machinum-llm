package machinum.service.gemma;

import machinum.TestApplication;
import machinum.extract.service.AbstractGrammarEditorTest;
import org.springframework.boot.test.context.SpringBootTest;

@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.copy-editing.model=gemma2:27b"
                , "app.translate.copy-editing.temperature=0.3"
                , "app.translate.copy-editing.numCtx=8192"

                , "app.translate.copy-editing-scoring.model=gemma2:27b"
                , "app.translate.copy-editing-scoring.numCtx=8192"
        }
)
public class GemmaGrammarEditorTest extends AbstractGrammarEditorTest {
}
