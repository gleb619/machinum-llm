package machinum.service.openchat;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.translate.model=openchat:7b-v3.5-0106-fp16"
                , "app.translate.temperature=0.8"
        }
)
public class OpenchatTranslaterDegreeTest extends AbstractTranslaterTest {
}
