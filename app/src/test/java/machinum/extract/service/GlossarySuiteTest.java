package machinum.extract.service;

import machinum.service.gemma.Gemma3GlossaryDegreeTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
//        DeepseekGlossaryTest.class,
//        QwenGlossaryTest.class,
//        Llama3GlossaryTest.class,
        Gemma3GlossaryDegreeTest.class
})
public class GlossarySuiteTest {
}
