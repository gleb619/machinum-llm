package machinum.service;


import machinum.service.llama.Llama33GlossaryExtractorTest;
import machinum.service.mistral.MistralNemoGlossaryExtractorTest;
import machinum.service.phi4.Phi4GlossaryExtractorTest;
import machinum.service.qwen.QwenGlossaryExtractorTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
        QwenGlossaryExtractorTest.class,
        Phi4GlossaryExtractorTest.class,
        Llama33GlossaryExtractorTest.class,
        MistralNemoGlossaryExtractorTest.class
})
public class GlossaryExtractorSuiteTest {
}
