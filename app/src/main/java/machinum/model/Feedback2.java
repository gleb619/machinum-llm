package machinum.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import machinum.processor.core.JsonSupport.JsonDescription;
import machinum.processor.core.StringSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.firstNotEmpty;
import static org.springframework.util.CollectionUtils.isEmpty;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@JsonDescription("Provide detailed feedback explaining the score, highlighting strengths and areas for improvement.")
public class Feedback2 implements StringSupport, Comparable<Feedback2> {

    @NotNull
    @Builder.Default
    @JsonDescription("The translation accurately reflects the meaning of the original English text.")
    private ScoreComment2 accuracy = ScoreComment2.createNew();

    @NotNull
    @Builder.Default
    @JsonDescription("The translation sounds natural and fluent for a Russian native speaker.")
    private ScoreComment2 fluency = ScoreComment2.createNew();

    @NotNull
    @Builder.Default
    @JsonDescription("The translation is consistent with the glossary, previous translations, and the overall tone of the text.")
    private ScoreComment2 consistency = ScoreComment2.createNew();

    @NotNull
    @Builder.Default
    @JsonDescription("The translation correctly uses terms and names as defined in the glossary.")
    private ScoreComment2 adherenceToGlossary = ScoreComment2.createNew();

    @NotNull
    @Builder.Default
    @JsonDescription("Culturally specific references or idioms are adapted appropriately for a Russian audience.")
    private ScoreComment2 culturalAdaptation = ScoreComment2.createNew();

    @NotNull
    @Size(max = 20)
    @Builder.Default
    @JsonDescription("Suggestions for improvement in plain text list")
    private List<String> suggestions = new ArrayList<>();

    public static Feedback2 createNew() {
        return Feedback2.builder()
                .build();
    }

    @Override
    public String stringValue() {
        var map = new HashMap<String, ScoreComment2>();
        map.put("Accuracy", accuracy);
        map.put("Fluency", fluency);
        map.put("Consistency", consistency);
        map.put("Adherence to glossary", adherenceToGlossary);
        map.put("Cultural adaptation", culturalAdaptation);

        var text = firstNotEmpty(map.entrySet().stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .map(entry -> entry.getValue().stringValue().formatted(entry.getKey()))
                .collect(Collectors.joining("\n")), "None");

        String text2;
        if (!isEmpty(suggestions)) {
            text2 = String.join("\n", suggestions);
        } else {
            text2 = "None";
        }

        return """
                Feedback:
                %s
                
                Suggestions for Improvement:
                %s
                """.formatted(text, text2);
    }

    @Override
    public int compareTo(Feedback2 other) {
        // Compare accuracy first
        int accuracyComparison = this.accuracy.compareTo(other.accuracy);
        if (accuracyComparison != 0) {
            return accuracyComparison;
        }

        // Then compare fluency
        int fluencyComparison = this.fluency.compareTo(other.fluency);
        if (fluencyComparison != 0) {
            return fluencyComparison;
        }

        // Then compare consistency
        int consistencyComparison = this.consistency.compareTo(other.consistency);
        if (consistencyComparison != 0) {
            return consistencyComparison;
        }

        // Then compare adherence to glossary
        int adherenceComparison = this.adherenceToGlossary.compareTo(other.adherenceToGlossary);
        if (adherenceComparison != 0) {
            return adherenceComparison;
        }

        // Finally compare cultural adaptation
        return this.culturalAdaptation.compareTo(other.culturalAdaptation);
    }

}
