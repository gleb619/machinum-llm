package org.springframework.ai.converter;

import org.mapstruct.Mapper;
import org.springframework.ai.ollama.api.OllamaOptions;

@Mapper(componentModel = "spring")
public interface OllamaOptionsMapper {

    OllamaOptions copy(OllamaOptions ollamaOptions);

}
