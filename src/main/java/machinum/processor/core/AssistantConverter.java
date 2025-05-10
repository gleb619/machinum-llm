package machinum.processor.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.extract.AbstractGlossaryTranslate.TranslatedName;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantConverter {

    public Object convert(Map<String, String> properties, ParameterizedTypeReference<?> typeRef) {
        var result = check(properties, typeRef, TranslatedName.class, map -> {
            var list = new ArrayList<TranslatedName>();
            map.forEach((key, value) -> list.add(TranslatedName.of(key, value)));

            return list;
        });

        if (result != null) {
            return result;
        }

        log.error("Can't find mapper for given class: {}", typeRef);
        throw new IllegalArgumentException("Unknown type: " + typeRef);
    }

    private Object check(Map<String, String> properties, ParameterizedTypeReference<?> typeRef, Class<?> clazz,
                         Function<Map<String, String>, Object> mapper) {
        Type type = typeRef.getType();
        if (type instanceof ParameterizedType parameterizedType) {

            // Get raw type and type arguments
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            // Simulate switch-case behavior
            if (rawType == List.class && typeArguments.length == 1 && typeArguments[0] == clazz) {
                return mapper.apply(properties);
            }
        }

        return null;
    }

}
