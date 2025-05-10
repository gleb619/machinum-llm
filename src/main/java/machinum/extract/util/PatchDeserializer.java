package machinum.extract.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer for Patch class
 */
public class PatchDeserializer extends StdDeserializer<Patch<String>> {

    public PatchDeserializer() {
        this(null);
    }

    public PatchDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Patch<String> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        List<AbstractDelta<String>> deltas = new ArrayList<>();

        if (node.has("deltas")) {
            JsonNode deltasNode = node.get("deltas");
            if (deltasNode.isArray()) {
                for (JsonNode deltaNode : deltasNode) {
                    AbstractDelta<String> delta = deserializeDelta(deltaNode);
                    if (delta != null) {
                        deltas.add(delta);
                    }
                }
            }
        }

        // Using DiffUtils to create a patch with the deserialized deltas
        var patch = new Patch<String>(deltas.size());
        deltas.forEach(patch::addDelta);

        return patch;
    }

    private AbstractDelta<String> deserializeDelta(JsonNode deltaNode) throws IOException {
        if (!deltaNode.has("type") || !deltaNode.has("source") || !deltaNode.has("target")) {
            return null;
        }

        DeltaType type = DeltaType.valueOf(deltaNode.get("type").asText());
        Chunk<String> source = deserializeChunk(deltaNode.get("source"));
        Chunk<String> target = deserializeChunk(deltaNode.get("target"));

        return switch (type) {
            case CHANGE -> new ChangeDelta<>(source, target);
            case DELETE -> new DeleteDelta<>(source, target);
            case INSERT -> new InsertDelta<>(source, target);
            case EQUAL -> new EqualDelta<>(source, target);
            default -> throw new IOException("Unknown delta type: " + type);
        };
    }

    private Chunk<String> deserializeChunk(JsonNode chunkNode) throws IOException {
        if (!chunkNode.has("position") || !chunkNode.has("lines")) {
            throw new IOException("Invalid chunk format: missing required fields");
        }

        int position = chunkNode.get("position").asInt();
        List<String> lines = new ArrayList<>();

        JsonNode linesNode = chunkNode.get("lines");
        if (linesNode.isArray()) {
            for (JsonNode lineNode : linesNode) {
                lines.add(lineNode.asText());
            }
        }

        List<Integer> changePosition = null;
        if (chunkNode.has("changePosition") && !chunkNode.get("changePosition").isNull()) {
            changePosition = new ArrayList<>();
            JsonNode changePositionNode = chunkNode.get("changePosition");
            if (changePositionNode.isArray()) {
                for (JsonNode posNode : changePositionNode) {
                    changePosition.add(posNode.asInt());
                }
            }
        }

        return new Chunk<>(position, lines, changePosition);
    }

}
