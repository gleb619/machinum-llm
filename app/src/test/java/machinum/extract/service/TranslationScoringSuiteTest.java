package machinum.extract.service;

import machinum.service.tlite.TproTranslationScoringTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

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
