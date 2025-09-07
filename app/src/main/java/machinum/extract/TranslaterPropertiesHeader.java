package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Getter
@Component
@RequiredArgsConstructor
public class TranslaterPropertiesHeader extends AbstractTranslaterHeader {

    @Value("classpath:prompts/custom/system/TranslateHeaderSystem.properties.ST")
    protected final Resource systemTemplate;

    @Value("classpath:prompts/custom/TranslateHeader.properties.ST")
    protected final Resource translateTemplate;

}
