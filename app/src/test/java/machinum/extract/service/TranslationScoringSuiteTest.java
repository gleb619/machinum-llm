package machinum.extract.service;

import machinum.service.tlite.TproTranslationScoringTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@EnabledIfSystemProperty(named = "llmAllowed", matches = "true")
@Suite
@SelectClasses({
//        GemmaTranslationScoringTest.class,
//        DeepseekDTranslationScoringTest.class,
//        SaigaGemmaTranslationScoringTest.class,
//        SaigaNemoTranslationScoringTest.class,
        TproTranslationScoringTest.class,
})
public class TranslationScoringSuiteTest {
}
