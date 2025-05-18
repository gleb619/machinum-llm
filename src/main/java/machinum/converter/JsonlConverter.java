package machinum.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.impl.Util;
import machinum.model.Chapter;
import machinum.util.TextUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.isEmpty;
import static machinum.util.TextUtil.isNotEmpty;

@Slf4j
@RequiredArgsConstructor
public class JsonlConverter {

    private final TypeReference<Chapter> typeReference;

    private final ObjectMapper objectMapper;

    @SneakyThrows
    public List<Chapter> convert(Lines source) {
        var jsonObjects = new ArrayList<Chapter>();
        String[] lines = source.lines().split("\\r?\\n");

        for (String line : lines) {
            var object = objectMapper.readValue(line, typeReference);
            jsonObjects.add(process(object));
        }

        return jsonObjects;
    }

    public List<Chapter> convert(String source) {
        return convert(new Lines(source));
    }

    public <U> String toJsonl(List<U> list) {
        return list.stream()
                .map(o -> {
                    try {
                        return objectMapper.writeValueAsString(o);
                    } catch (JsonProcessingException e) {
                        return ExceptionUtils.rethrow(e);
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    private Chapter process(@NonNull Chapter chapter) {
        if (isEmpty(chapter.getTitle()) && isNotEmpty(chapter.getText())) {
            var header = Arrays.stream(chapter.getText().split("[\r\n]"))
                    .filter(TextUtil::isNotEmpty)
                    .findFirst()
                    .orElse("");
            chapter.setTitle(header);
            chapter.setText(chapter.getText().replaceFirst(Pattern.quote(header), ""));
        }

        return chapter;
    }

    /* ============= */

    public record Lines(String lines) {
    }

}
