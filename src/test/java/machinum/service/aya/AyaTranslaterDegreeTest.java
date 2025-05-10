package machinum.service.aya;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@Deprecated
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=hf.co/bartowski/aya-expanse-32b-GGUF:Q6_K"
                , "app.translate.temperature=0.8"
        }
)
public class AyaTranslaterDegreeTest extends AbstractTranslaterTest {
}
