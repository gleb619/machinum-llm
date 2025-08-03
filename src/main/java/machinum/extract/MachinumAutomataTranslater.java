package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.service.MachinumAutomataRestClient;
import org.springframework.beans.factory.annotation.Autowired;

import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@RequiredArgsConstructor
public abstract class MachinumAutomataTranslater {

    @Autowired
    protected MachinumAutomataRestClient machinumAutomataRestClient;

    protected String doTranslate(String text) {
        log.debug("Translating: text={}...", toShortDescription(text));

        String translatedText = machinumAutomataRestClient.translateScript(text)
                .getTranslatedText();

        log.debug("Translated: text={}...", toShortDescription(translatedText));

        return translatedText;
    }

}
