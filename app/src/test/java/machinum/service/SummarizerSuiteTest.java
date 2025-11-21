package machinum.service;

import machinum.service.gemma.Gemma3SummarizerDegreeTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
//        TproSummarizerTest.class,
//        GemmaSummarizerTest.class,
        Gemma3SummarizerDegreeTest.class,
//        QwenSummarizerTest.class,
//        QwenSummarizerDegreeTest.class,
//        DeepseekSummarizerTest.class,
//        GigaChatSummarizerTest.class,
//        OpenbuddySummarizerTest.class,
//        Phi4SummarizerTest.class,
//        Llama33SummarizerTest.class,
//        MistralNemoSummarizerTest.class,
})
public class SummarizerSuiteTest {

}
