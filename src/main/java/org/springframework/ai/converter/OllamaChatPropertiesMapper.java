package org.springframework.ai.converter;

import org.mapstruct.Mapper;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;

@Mapper(componentModel = "spring", imports = {
        OllamaOptionsMapper.class
})
public interface OllamaChatPropertiesMapper {

    OllamaChatProperties copy(OllamaChatProperties ollamaChatProperties);

}
