package machinum.extract;

import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.TypeScope;
import machinum.processor.core.AssistantContext;
import machinum.processor.core.JsonSupport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlossaryTranslate extends AbstractGlossaryTranslate {

    @Getter
    @Value("classpath:prompts/custom/system/GlossaryTranslateSystem.ST")
    private final Resource systemTemplate;

    @Getter
    @Value("classpath:prompts/custom/GlossaryExtractor-translate.ST")
    private final Resource glossaryTemplate;

    @Override
    public AssistantContext.OutputType getOutputType() {
        return AssistantContext.OutputType.PROPERTIES;
    }

}
