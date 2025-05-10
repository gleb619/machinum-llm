package machinum.util;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;

import java.lang.reflect.Type;

@RequiredArgsConstructor
public class CustomTypeReference extends TypeReference<Object> {

    private final Type type;

    public static CustomTypeReference of(ParameterizedTypeReference<?> pt) {
        return new CustomTypeReference(pt.getType());
    }

    @Override
    public Type getType() {
        return type;
    }

}