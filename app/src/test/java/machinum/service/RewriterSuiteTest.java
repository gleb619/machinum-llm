package machinum.service;

import machinum.service.gemma.Gemma3RewriterDegreeTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
//        GemmaRewriterTest.class,
//        GemmaRewriterDegreeTest.class,
        Gemma3RewriterDegreeTest.class,
//        TproRewriterTest.class,

//        DeepseekRewriterTest.class,
//        QwenRewriterTest.class,
//        GigaChatRewriterTest.class,
//        OpenbuddyRewriterTest.class,

//        Phi4RewriterTest.class,
//        Llama33RewriterTest.class,
//        MistralNemoRewriterTest.class,
//        MistralRewriterTest.class,
//        S1RewriterTest.class,
})
public class RewriterSuiteTest {
}
