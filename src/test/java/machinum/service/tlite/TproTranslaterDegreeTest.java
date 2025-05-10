package machinum.service.tlite;

import machinum.TestApplication;
import machinum.service.base.AbstractTranslaterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
//                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q8_0-GGUF:latest"
                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q6_K-GGUF:latest"
//                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q5_K_M-GGUF:latest"
//                "app.translate.model=hf.co/t-tech/T-pro-it-1.0-Q4_K_M-GGUF:latest"

                , "app.translate.temperature=0.8"
                , "app.translate.numCtx=10240"
//                ,"app.translate.numCtx=16384"

                , "app.translate.scoring.model=hf.co/t-tech/T-pro-it-1.0-Q6_K-GGUF:latest"
                , "app.translate.scoring.numCtx=10240"
        }
)
public class TproTranslaterDegreeTest extends AbstractTranslaterTest {
}
