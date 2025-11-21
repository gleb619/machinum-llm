package machinum.extract.service;

import machinum.service.gemma.Gemma3GlossaryDegreeTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
//        DeepseekGlossaryTest.class,
//        QwenGlossaryTest.class,
//        Llama3GlossaryTest.class,
        Gemma3GlossaryDegreeTest.class
})
public class GlossarySuiteTest {
}
