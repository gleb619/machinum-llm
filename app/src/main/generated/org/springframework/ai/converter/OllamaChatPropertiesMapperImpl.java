package org.springframework.ai.converter;

import javax.annotation.processing.Generated;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:49:42+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class OllamaChatPropertiesMapperImpl implements OllamaChatPropertiesMapper {

    @Override
    public OllamaChatProperties copy(OllamaChatProperties ollamaChatProperties) {
        if ( ollamaChatProperties == null ) {
            return null;
        }

        OllamaChatProperties ollamaChatProperties1 = new OllamaChatProperties();

        ollamaChatProperties1.setModel( ollamaChatProperties.getModel() );

        return ollamaChatProperties1;
    }
}
