package machinum.service;

import machinum.service.deepseek.DeepseekProofreaderTestEn;
import machinum.service.gemma.GemmaProofreaderTestEn;
import machinum.service.mistral.MistralNemoProofreaderTestEn;
import machinum.service.openbuddy.OpenbuddyProofreaderTestEn;
import machinum.service.qwen.QwenProofreaderTestEn;
import machinum.service.tlite.TproProofreaderTestEn;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
        GemmaProofreaderTestEn.class,
        TproProofreaderTestEn.class,
        QwenProofreaderTestEn.class,
        DeepseekProofreaderTestEn.class,
//        GigaChatProofreaderTest.class,
        OpenbuddyProofreaderTestEn.class,
        MistralNemoProofreaderTestEn.class,
})
public class ProofreaderEnSuiteTest {
}
