package machinum.service.saiga;

import machinum.TestApplication;
import machinum.extract.service.AbstractGrammarEditorTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.copy-editing.model=hf.co/IlyaGusev/saiga_nemo_12b_gguf:BF16"
                , "app.translate.copy-editing.temperature=0.3"

                , "app.translate.copy-editing-scoring.model=hf.co/IlyaGusev/saiga_nemo_12b_gguf:BF16"
        }
)
public class SaigaNemoGrammarEditorTest extends AbstractGrammarEditorTest {
}
