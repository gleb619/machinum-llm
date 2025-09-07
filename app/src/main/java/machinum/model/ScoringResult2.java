package machinum.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import machinum.flow.model.StringSupport;
import machinum.processor.core.JsonSupport.JsonDescription;

import java.util.Objects;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@JsonDescription("The scoring for a chapter’s text translation from English to Russian.")
public class ScoringResult2 implements StringSupport, Comparable<ScoringResult2> {

    @Min(0)
    @Max(10)
    @NotNull
    @JsonDescription("""
            Scoring System:
            
            0-2: Poor (significant errors, awkward phrasing, or major inconsistencies).
            
            3-4: Fair (some errors or inconsistencies, but the overall meaning is preserved).
            
            5-6: Good (minor errors, mostly fluent and consistent).
            
            7-8: Very Good (few errors, fluent and natural, adheres well to the glossary).
            
            9-10: Excellent (no errors, perfectly fluent, natural, and consistent).
            """)
    private double score;

    @NotNull
    @JsonDescription("A detailed feedback.")
    private Feedback2 feedback;

    public static ScoringResult2 createNew() {
        return ScoringResult2.builder()
                .score(0)
                .feedback(Feedback2.createNew())
                .build();
    }

    public boolean empty() {
        return Objects.isNull(feedback) || Objects.equals(score, 0d);
    }

    @Override
    public String stringValue() {
        return """
                Score:\s
                %s
                
                %s
                """.formatted(score, feedback.stringValue());
    }

    @Override
    public int compareTo(ScoringResult2 other) {
        int result = Double.compare(other.score, this.score);
        if (result != 0) {
            return result;
        }

        // If scores are equal, compare feedback
        return this.feedback.compareTo(other.feedback);
    }

}
