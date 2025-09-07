package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranslaterBody extends AbstractTranslaterBody {

    @Getter
    @Value("classpath:prompts/custom/system/TranslateBodySystem.ST")
    private final Resource systemTemplate;

    @Getter
    @Value("classpath:prompts/custom/TranslateBody.ST")
    private final Resource translateTemplate;

}
