package machinum.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

@Slf4j
@Configuration
public class ChatClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CustomChatClientBuilderConfigurer chatClientBuilderConfigurer(ObjectProvider<ChatClientCustomizer> customizerProvider) {
        CustomChatClientBuilderConfigurer configurer = new CustomChatClientBuilderConfigurer();
        configurer.setCustomizers(customizerProvider.orderedStream().toList());
        return configurer;
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    ChatClient.Builder chatClientBuilder(CustomChatClientBuilderConfigurer chatClientBuilderConfigurer, OllamaChatModel chatModel,
                                         ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<ChatClientObservationConvention> observationConvention) {
        ChatClient.Builder builder = ChatClient.builder(chatModel, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP), observationConvention.getIfUnique(() -> null));
        return chatClientBuilderConfigurer.configure(builder);
    }

    @Data
    public class CustomChatClientBuilderConfigurer {

        private List<ChatClientCustomizer> customizers;

        public ChatClient.Builder configure(ChatClient.Builder builder) {
            this.applyCustomizers(builder);
            return builder;
        }

        private void applyCustomizers(ChatClient.Builder builder) {
            if (this.customizers != null) {
                for (ChatClientCustomizer customizer : this.customizers) {
                    customizer.customize(builder);
                }
            }

        }
    }

}
