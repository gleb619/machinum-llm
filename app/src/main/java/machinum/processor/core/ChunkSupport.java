package machinum.processor.core;

import lombok.SneakyThrows;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ChunkSupport {

    private static Map<String, Object> newMap0(Object... v) {
        assert v.length % 2 == 0;
        var out = new HashMap<String, Object>();
        for (int i = 0; i < v.length; i += 2) out.put((String) v[i], v[i + 1]);
        return out;
    }

    @SneakyThrows
    default Resource createResource(Resource template, List<String> chunks) {
        var templateContent = StreamUtils.copyToString(template.getInputStream(), StandardCharsets.UTF_8);
        var builder = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            var index = i + 1;

            builder.append("Chunk %s:\n".formatted(index))
                    .append("{text%s}\n\n".formatted(index));
        }

        var result = builder.toString()
                .replace("{text1}", "{text}");

        return new ByteArrayResource(templateContent.replace("{text}", result)
                .getBytes());
    }

    default Map<String, String> createInputs(List<String> chunks, String... additionalEntries) {
        var output = new HashMap<String, String>();

        for (int i = 0; i < chunks.size(); i++) {
            var index = i + 1;

            String chunk = chunks.get(i);

            if (i == 0) {
                output.put("text", chunk);
            } else {
                output.put("text%s".formatted(index), chunk);
            }
        }

        newMap0(additionalEntries).forEach((k, v) -> output.put(k, (String) v));

        return output;
    }

}
