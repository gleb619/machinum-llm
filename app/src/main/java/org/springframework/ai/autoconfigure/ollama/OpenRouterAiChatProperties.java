package org.springframework.ai.autoconfigure.ollama;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ConfigurationProperties(prefix = "spring.ai.openrouter.chat")
public class OpenRouterAiChatProperties {

    private Options options = new Options();

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Options {

        private Set<String> models;

        public Set<String> acquireModels() {
            if (models.isEmpty()) {
                return Collections.emptySet();
            }

            String first = models.iterator().next();

            if (first.contains(";")) {
                return new LinkedHashSet<>(List.of(first.split(";")));
            }

            return models;
        }

    }


}
