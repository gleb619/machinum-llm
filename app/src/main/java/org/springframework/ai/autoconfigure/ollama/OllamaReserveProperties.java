package org.springframework.ai.autoconfigure.ollama;

import lombok.*;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import static machinum.config.Constants.DEFAULT_MODEL;

@Deprecated
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ConfigurationProperties("spring.ai.ollama.reserve")
public class OllamaReserveProperties {

    private String baseUrl = "http://localhost:7869";

    private boolean enabled = true;

    @Builder.Default
    @NestedConfigurationProperty
    private OllamaOptions options = OllamaOptions.builder()
            .model(DEFAULT_MODEL)
            .build();

}
