package org.springframework.ai.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Custom Jackson deserializer for {@link AssistantMessage}.
 * <p>
 * This deserializer reads the JSON payload and constructs an AssistantMessage using:
 * <ul>
 *   <li>text from the "text" field</li>
 *   <li>metadata from the "metadata" field</li>
 *   <li>toolCalls from the "toolCalls" array (if present)</li>
 *   <li>media from the "media" array (if present)</li>
 * </ul>
 * <p>
 * Note: Register this deserializer by either annotating AssistantMessage with
 * {@code @JsonDeserialize(using = AssistantMessageDeserializer.class)}
 * or by registering it in your Jackson module.
 */
@Slf4j
public class AssistantMessageDeserializer extends StdDeserializer<AssistantMessage> {

    public AssistantMessageDeserializer() {
        super(AssistantMessage.class);
    }

    @Override
    public AssistantMessage deserialize(JsonParser parser, DeserializationContext ctxt)
            throws IOException {

        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);

        // Extract text content
        String content = node.path("text").asText();

        // Deserialize metadata (if present) as a Map
        JsonNode metadataNode = node.path("metadata");
        Map<String, Object> metadata = mapper.convertValue(metadataNode, Map.class);

        // Deserialize toolCalls (if present) as an array of AssistantMessage.ToolCall
        List<AssistantMessage.ToolCall> toolCalls;
        JsonNode toolCallsNode = node.get("toolCalls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            toolCalls = List.of(mapper.treeToValue(toolCallsNode, AssistantMessage.ToolCall[].class));
        } else {
            toolCalls = List.of();
        }

        // Deserialize media (if present) as a list (using Object type here, adjust if needed)
        List<Media> media;
        JsonNode mediaNode = node.get("media");
        if (mediaNode != null && mediaNode.isArray()) {
            media = List.of(mapper.treeToValue(mediaNode, Media[].class));
        } else {
            media = List.of();
        }

        return new AssistantMessage(content, metadata, toolCalls, media);
    }

}
