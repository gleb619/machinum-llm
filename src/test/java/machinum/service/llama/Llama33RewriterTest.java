package machinum.service.llama;

import machinum.TestApplication;
import machinum.service.AbstractRewriterTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "app.rewrite.model=llama3.3:70b-instruct-q2_K"
        }
)
public class Llama33RewriterTest extends AbstractRewriterTest {

}
