package machinum.service.saiga;

import machinum.TestApplication;
import machinum.service.base.AbstractGlossaryTranslateTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.glossary.translate.model=hf.co/IlyaGusev/saiga_yandexgpt_8b_gguf:BF16"
        }
)
public class SaigaYandexGlossaryTranslateTest extends AbstractGlossaryTranslateTest {
}
