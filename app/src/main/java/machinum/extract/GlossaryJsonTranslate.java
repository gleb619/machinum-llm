package machinum.extract;

import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.TypeScope;
import machinum.processor.core.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlossaryJsonTranslate extends AbstractGlossaryTranslate {

    public static final Function<MemberScope, String> DESCRIPTION_RESOLVER = JsonSupport.createComplexDescriptionResolver(TranslatedName.class);
    public static final Function<TypeScope, String> TYPE_DESCRIPTION_RESOLVER = JsonSupport.createTypeDescriptionResolver(TranslatedName.class);

    @Getter
    @Value("classpath:prompts/custom/system/GlossaryTranslateSystem.json.ST")
    private final Resource systemTemplate;

    @Getter
    @Value("classpath:prompts/custom/GlossaryExtractor-translate.json.ST")
    private final Resource glossaryTemplate;

    @Override
    public AssistantContext.OutputType getOutputType() {
        return AssistantContext.OutputType.JSON;
    }

}
