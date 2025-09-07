package machinum.controller;

import machinum.util.CodeBlockExtractor;
import lombok.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST Controller to handle translation requests using Spring AI ChatClient and Ollama.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TranslateController {

    private final ChatClient chatClient;

    @Value("classpath:prompts/misc/system/PlanTranslateSystem.ST")
    private final Resource systemTemplate;

    @Value("classpath:prompts/misc/PlanTranslate.ST")
    private final Resource userTemplate;

    /**
     * Handles POST requests to translate English text to Russian.
     * Expects a JSON body like: {"textToTranslate": "Hello, world!"}
     *
     * @param request The request body containing the text to translate.
     * @return The translated Russian text as a String.
     */
    @PostMapping("/translate/to-russian")
    public String translateToRussian(@RequestBody TranslateRequest request) {
        return work(request);
    }

    /**
     * Handles GET requests to translate English text to Russian.
     * Expects a query parameter 'text' like: /api/translate/to-russian?text=Hello%2C+world!
     *
     * @param text The text to be translated, provided as a query parameter.
     * @return The translated Russian text as a String.
     */
    @GetMapping("/translate/to-russian")
    public String translateToRussianSimple(@RequestParam("text") String text) {
        TranslateRequest request = new TranslateRequest(text);
        return work(request);
    }

    private String work(TranslateRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return "Error: No text provided for translation.";
        }

        var systemPromptTemplate = new SystemPromptTemplate(systemTemplate);
        var userPromptTemplate = new PromptTemplate(userTemplate);

        // Build the full prompt with system and user messages
        var prompt = new Prompt(
                List.of(systemPromptTemplate.createMessage(),
                        userPromptTemplate.createMessage(Map.of("text", request.getText())))
        );

        // Call the AI model via ChatClient
        var response = chatClient.prompt(prompt).call();

        // Return the content of the AI's response (the translated text)
        var content = response.content();
        var result = CodeBlockExtractor.extractCode(content);

        return Objects.requireNonNull(result, "Result can't be null").trim();
    }

    /**
     * Data Transfer Object (DTO) for the translation request.
     * Contains the text to be translated.
     */
    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class TranslateRequest {

        private String text;

    }

}
