package machinum.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import machinum.flow.model.StringSupport;
import machinum.processor.core.JsonSupport.JsonDescription;
import machinum.processor.core.JsonSupport.SchemaIgnore;

import java.util.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@JsonDescription("A glossary term.")
public class ObjectName implements StringSupport {

    public static final String RU_NAME = "ruName";
    public static final String SIMILAR = "similar";

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

    @JsonDescription("Russian translation of the term name.")
    private String ruName;

    @SchemaIgnore
    @JsonIgnore
    @Singular("metadata")
    private Map<String, Object> metadata = new HashMap<>();

    public static ObjectName forName(String name) {
        return ObjectName.builder()
                .name(name)
                .build();
    }

    @JsonAnySetter
    public void add(String key, String value) {
        if (RU_NAME.equals(key)) {
            this.ruName = value;
        } else {
            metadata.put(key, value);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getMap() {
        return metadata;
    }

    public ObjectName withRuName(@NonNull String value) {
        var map = new HashMap<>(getMetadata());
        if (this.ruName != null) {
            // Backup previous ruName to metadata for history
            int index = 1;
            while (map.containsKey(RU_NAME + index)) {
                index++;
            }
            map.put(RU_NAME + index, this.ruName);
        }

        return toBuilder()
                .ruName(value)
                .clearMetadata()
                .metadata(map)
                .build();
    }

    public boolean hasRuName() {
        return ruName != null;
    }

    public String ruName() {
        return Objects.requireNonNull(ruName, "Ru name can't be null or empty");
    }

    public boolean marked() {
        return Boolean.TRUE.equals(metadata.get("marked"));
    }

    public void marked(boolean marked) {
        metadata.put("marked", marked);
    }

    public Optional<String> optionalRuName() {
        return hasRuName() ? Optional.of(ruName()) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public List<NameSimilarity> similarNames() {
        return (List) metadata.getOrDefault(SIMILAR, new ArrayList<>());
    }

    public void similarNames(List<NameSimilarity> similarNames) {
        metadata.put(SIMILAR, similarNames);
    }

    public String getTranslationHistory(int version) {
        return Objects.toString(metadata.get(RU_NAME + version), null);
    }

    public List<String> getTranslationHistory() {
        List<String> history = new ArrayList<>();
        int index = 1;
        while (metadata.containsKey(RU_NAME + index)) {
            history.add(getTranslationHistory(index));
            index++;
        }

        return history;
    }

    @Override
    public String stringValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" - it's a ").append(category)
                .append("; Example of usage: ").append(description);

        if (references != null && !references.isEmpty()) {
            sb.append("; References: ").append(references);
        }
        if (hasRuName()) {
            sb.append("; Russian translation is: `").append(ruName()).append("`;");
            return sb.toString();
        }

        return sb.toString();
    }

    public String invertedStringValue() {
        return "`%s` - it's a %s; Example of usage: %s;  English translation is: `%s`;".formatted(
                ruName(), category, description, name);
    }

    @Override
    public String shortStringValue() {
        String string = "`%s` - it's a %s.".formatted(name, category);

        if (hasRuName()) {
            return "%s On Russian - `%s`;".formatted(string, ruName());
        }

        return string;
    }

}
