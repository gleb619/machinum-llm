package org.springframework.ai.autoconfigure.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.List;
import java.util.Map;

import static machinum.config.Constants.CHARACTERS_METADATA_KEY;

@Deprecated
@Slf4j
@RequiredArgsConstructor
public class CharactersMetadataEnricher implements DocumentTransformer {

    public static final String CONTEXT_STR_PLACEHOLDER = "context_str";

    public static final String JSON_FORMAT_PLACEHOLDER = "json_format";

    public static final String JSON_FORMAT_EMPTY_PLACEHOLDER = "json_format_empty";

    public static final String JSON_FORMAT = """
            {
              "characters": [
                {
                  "name": "CharacterName1",
                  "description": "Brief description of the character or their role in the chapter.",
                  "sex": "male/female/unknown"
                },
                {
                  "name": "CharacterName2",
                  "description": "Brief description of the character or their role in the chapter.",
                  "sex": "male/female/unknown"
                }
              ]
            }
            """;

    public static final String JSON_FORMAT_EMPTY = """
            {
              "characters": []
            }
            """;

    public static final String CHARACTERS_TEMPLATE = """
            You are an assistant tasked with analyzing text from a novel chapter. \
            Your goal is to extract all unique characters mentioned in the chapter and provide a brief description for each, \
            along with their identified or implied sex (male, female, or unknown if not specified). \
            Analyze the context carefully to generate concise and accurate descriptions based on the text.
            
            Output format:
            Provide the data in the following JSON format:
            
            {json_format}
            
            Return rule:
            If no characters are found or you're not sure, return an empty array:
            
            {json_format_empty}
            
            Input:
            {context_str}""";

    /**
     * Model predictor
     */
    private final ChatModel chatModel;

    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    public List<Document> apply(List<Document> documents) {
        for (Document document : documents) {
            var template = new PromptTemplate(CHARACTERS_TEMPLATE);
            Prompt prompt = template.create(Map.of(
                    CONTEXT_STR_PLACEHOLDER, document.getText(),
                    JSON_FORMAT_PLACEHOLDER, JSON_FORMAT,
                    JSON_FORMAT_EMPTY_PLACEHOLDER, JSON_FORMAT_EMPTY
            ));

            log.trace(">> {}", prompt.getContents());

            var result = this.chatModel.call(prompt).getResult();
            var output = result.getOutput();
            var characters = output.getText();

            log.trace("<< {}", characters);

            var map = objectMapper.readValue(characters, Map.class);

            document.getMetadata().put(CHARACTERS_METADATA_KEY, map.getOrDefault("characters", "[]"));
        }

        return documents;
    }

}
