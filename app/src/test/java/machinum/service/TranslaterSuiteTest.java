package machinum.service;

import machinum.service.ru_qwen.RuQwenTranslaterDegreeTest;
import machinum.service.ru_qwen.RuQwenTranslaterTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
//        GemmaTranslaterTest.class,
//        TliteTranslaterTest.class,
//        TproTranslaterTest.class,
//        TproTranslaterDegreeTest.class,
//        OpenchatTranslaterTest.class,
//        OpenchatTranslaterDegreeTest.class,
//        AyaTranslaterTest.class,
//        AyaTranslaterDegreeTest.class,
//        GigaChatTranslaterTest.class,
//        Vikhr1TranslaterTest.class,
//        Vikhr2TranslaterTest.class,
//        SaigaNemoTranslaterTest.class,
//        SainemoTranslaterTest.class,
//        RuQwenOldTranslaterTest.class,
//        OpenbuddyTranslaterTest.class,
        RuQwenTranslaterTest.class,
        RuQwenTranslaterDegreeTest.class,
})
public class TranslaterSuiteTest {
}
