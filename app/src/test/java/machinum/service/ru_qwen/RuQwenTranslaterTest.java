package machinum.service.ru_qwen;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q5_K_M"
//                "app.translate.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q6_K"

                , "app.translate.temperature=0.3"
                , "app.translate.numCtx=10240"

                , "app.translate.scoring.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q5_K_M"
//                ,"app.translate.scoring.model=hf.co/RefalMachine/RuadaptQwen2.5-32B-Pro-Beta-GGUF:Q6_K"

        }
)
public class RuQwenTranslaterTest extends AbstractTranslaterTest {
}
