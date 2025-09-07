package machinum.model;

import lombok.*;
import machinum.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SimpleChapter {

    private String key;

    private String value;

    @Deprecated
    public static SimpleChapter of(Chapter info) {
        return of(info.getSourceKey(), info.getTranslatedTitle(), info.getTranslatedText());
    }

    public static SimpleChapter of(String key, String title, String body) {
        return SimpleChapter.builder()
                .key(key)
                .value("""
                        %s
                        %s""".formatted(title, body))
                .build();
    }

    public List<String> lines() {
        return value.lines().collect(Collectors.toList());
    }

    public String header() {
        return lines().iterator().next();
    }

    public List<String> body() {
        var output = new ArrayList<>(lines());
        output.remove(0);

        return output;
    }

    public String bodyString() {
        return String.join("\n", body());
    }

    @Override
    public String toString() {
        return "Chapter{" +
                "key='" + key + '\'' +
                ", value='" + TextUtil.toShortDescription(value) + '\'' +
                '}';
    }

}
