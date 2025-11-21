package machinum.service;

import machinum.service.saiga.SaigaNemoGrammarEditorDegreeTest;
import machinum.service.saiga.SaigaNemoGrammarEditorTest;
import machinum.service.tlite.TproGrammarEditorDegreeTest;
import machinum.service.tlite.TproGrammarEditorTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
//        GemmaGrammarEditorTest.class,
//        GemmaGrammarEditorDegreeTest.class,
//        SaigaGemmaGrammarEditorTest.class,
//        SaigaGemmaGrammarEditorDegreeTest.class,
        SaigaNemoGrammarEditorTest.class,
        SaigaNemoGrammarEditorDegreeTest.class,
        TproGrammarEditorTest.class,
        TproGrammarEditorDegreeTest.class,
//        DeepseekDGrammarEditorTest.class,
//        DeepseekDGrammarEditorDegreeTest.class,
})
public class GrammarEditorSuiteTest {
}
