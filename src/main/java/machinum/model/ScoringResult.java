package machinum.model;

import lombok.*;
import machinum.processor.core.JsonSupport.JsonDescription;
import machinum.processor.core.StringSupport;

import java.util.*;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.firstNotEmpty;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@JsonDescription("The scoring for a chapter’s text translation from English to Russian.")
public class ScoringResult implements StringSupport, Comparable<ScoringResult> {

    @Builder.Default
    @JsonDescription("The translation sounds natural and fluent for a Russian native speaker.")
    private ScoreComment fluency = ScoreComment.createNew();

    @Builder.Default
    @JsonDescription("The translation accurately reflects the meaning of the original English text.")
    private ScoreComment accuracy = ScoreComment.createNew();

    @Builder.Default
    @JsonDescription("Culturally specific references or idioms are adapted appropriately for a Russian audience.")
    private ScoreComment adaptation = ScoreComment.createNew();

    @Builder.Default
    @JsonDescription("The translation is consistent with the glossary, previous translations, and the overall tone of the text.")
    private ScoreComment coherence = ScoreComment.createNew();

    @Builder.Default
    @JsonDescription("The translation correctly uses terms and names as defined in the glossary.")
    private ScoreComment terminology = ScoreComment.createNew();

    public static boolean isEmpty(ScoringResult scoringResult) {
        return Objects.isNull(scoringResult.fluency) || Objects.isNull(scoringResult.accuracy) ||
                Objects.isNull(scoringResult.adaptation) || Objects.isNull(scoringResult.coherence) ||
                Objects.isNull(scoringResult.terminology) ||
                ScoreComment.isEmpty(scoringResult.fluency) || ScoreComment.isEmpty(scoringResult.accuracy) ||
                ScoreComment.isEmpty(scoringResult.adaptation) || ScoreComment.isEmpty(scoringResult.coherence) ||
                ScoreComment.isEmpty(scoringResult.terminology);
    }

    public static ScoringResult createNew() {
        return ScoringResult.builder()
                .fluency(ScoreComment.createNew())
                .accuracy(ScoreComment.createNew())
                .adaptation(ScoreComment.createNew())
                .coherence(ScoreComment.createNew())
                .terminology(ScoreComment.createNew())
                .build();
    }

    public Map<String, ScoreComment> topicsAsMap() {
        var map = new HashMap<String, ScoreComment>();
        map.put("Fluency", fluency);
        map.put("Accuracy", accuracy);
        map.put("Cultural adaptation", adaptation);
        map.put("Consistency", coherence);
        map.put("Terminology", terminology);

        return map;
    }

    @Override
    public String stringValue() {
        var map = topicsAsMap();

        var text = firstNotEmpty(map.entrySet().stream()
                .filter(entry -> Objects.nonNull(entry.getValue()))
                .map(entry -> entry.getValue().stringValue().formatted(entry.getKey()))
                .collect(Collectors.joining("\n")), "None");

        var text2 = firstNotEmpty(map.entrySet().stream()
                        .filter(entry -> Objects.nonNull(entry.getValue().getSuggestions()))
                        .flatMap(entry -> entry.getValue().getSuggestions().stream())
                        .collect(Collectors.joining("\n"))
                , "None");

        var score = score();

        return """
                Score:
                %s
                                
                Feedback:
                %s
                                    
                Suggestions for Improvement:
                %s
                """.formatted(score, text, text2);
    }

    @Override
    public int compareTo(ScoringResult other) {
        // Compare fluency first
        int fluencyComparison = this.fluency.compareTo(other.fluency);
        if (fluencyComparison != 0) {
            return fluencyComparison;
        }

        // Then compare accuracy
        int accuracyComparison = this.accuracy.compareTo(other.accuracy);
        if (accuracyComparison != 0) {
            return accuracyComparison;
        }

        // Then compare cultural adaptation
        int culturalComparison = this.adaptation.compareTo(other.adaptation);
        if (culturalComparison != 0) {
            return culturalComparison;
        }

        // Then compare consistency
        int consistencyComparison = this.coherence.compareTo(other.coherence);
        if (consistencyComparison != 0) {
            return consistencyComparison;
        }

        // Finally compare adherence to glossary
        return this.terminology.compareTo(other.terminology);
    }

    public double score() {
        return Math.round(topicsAsMap().values().stream()
                .map(ScoreComment::getScore)
                .mapToDouble(d -> d)
                .average()
                .orElse(0.0));
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ScoreComment implements StringSupport, Comparable<ScoreComment> {

        @JsonDescription("Score between 0 and 10.")
        private int score;

        @JsonDescription("Comment describing the evaluation.")
        private String comment;

        @Builder.Default
        @JsonDescription("Suggestions for improvement.")
        private List<String> suggestions = new ArrayList<>();

        public static ScoreComment createNew() {
            return ScoreComment.builder().build();
        }

        public static boolean isEmpty(ScoreComment scoreComment) {
            return Double.compare(scoreComment.score, 0d) == 0;
        }

        @Override
        public String stringValue() {
            return "`%s` - score is %s. %s".formatted("%s", Math.round(score), comment.replace("%", "%%"));
        }

        @Override
        public int compareTo(ScoreComment other) {
            return Double.compare(other.score, this.score);
        }

    }

}
