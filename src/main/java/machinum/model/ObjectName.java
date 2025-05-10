package machinum.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import machinum.processor.core.JsonSupport.JsonDescription;
import machinum.processor.core.JsonSupport.SchemaIgnore;
import machinum.processor.core.StringSupport;

import java.util.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@JsonDescription("A glossary term.")
public class ObjectName implements StringSupport {

    public static final String RU_NAME = "ruName";

    @NotNull
    @NotEmpty
    @JsonDescription("A clear, concise identifier of the term.")
    private String name;

    @NotNull
    @NotEmpty
    @JsonDescription("A short explanation of what the term means within the context of the text(e.g Character/Location/Place/Ability/Legacy/Object/Event/Theme/Relationship/Conflict/Goal/Symbol/Emotion/Time/Faction/Power/Mystery/History/Culture/Transformation e.g).")
    private String category;

    @NotNull
    @NotEmpty
    @JsonDescription("A statement or account that describes the term. Or providing examples of how the term is used or alternative phrasing that could be substituted.")
    private String description;

    @NotNull
    @NotEmpty
    @Builder.Default
    @JsonDescription("A list of names of related terms (e.g., 'Kael’s Village' references 'Kael', etc).")
    private List<String> references = new ArrayList<>();

    @SchemaIgnore
    @JsonIgnore
    @Singular("metadata")
    private Map<String, String> metadata = new HashMap<>();

    public static ObjectName forName(String name) {
        return ObjectName.builder()
                .name(name)
                .build();
    }

    @JsonAnySetter
    public void add(String key, String value) {
        metadata.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, String> getMap() {
        return metadata;
    }

    public ObjectName ruName(String ruName) {
        return toBuilder()
                .metadata(RU_NAME, ruName)
                .build();
    }

    public boolean hasRuName() {
        return metadata.containsKey(RU_NAME);
    }

    public String ruName() {
        return Objects.requireNonNull(metadata.get(RU_NAME), "Ru name can't be null or empty");
    }

    public Optional<String> optionalRuName() {
        return hasRuName() ? Optional.of(ruName()) : Optional.empty();
    }

    @Override
    public String stringValue() {
        String string = "`%s` - it's a %s; Example of usage: %s;".formatted(name, category, description);

        if (metadata.containsKey(RU_NAME)) {
            return "%s Russian translation is: `%s`;".formatted(string, ruName());
        }

        return string;
    }

    public String invertedStringValue() {
        String string = "`%s` - it's a %s; Example of usage: %s;".formatted(ruName(), category, description);

        if (metadata.containsKey(RU_NAME)) {
            return "%s English translation is: `%s`;".formatted(string, name);
        }

        return string;
    }

    @Override
    public String shortStringValue() {
        return "`%s` - it's a %s. On Russian - `%s`;".formatted(name, category, ruName());
    }

}
