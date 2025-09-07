package machinum.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import machinum.processor.core.JsonSupport.JsonDescription;
import machinum.processor.core.StringSupport;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ScoreComment2 implements StringSupport, Comparable<ScoreComment2> {

    @Min(0)
    @Max(10)
    @NotNull
    @JsonDescription("""
            Scoring System:
            
            0-2: Poor
            
            3-4: Fair
            
            5-6: Good
            
            7-8: Very Good
            
            9-10: Excellent
            """)
    private double score;

    @JsonDescription("A detailed comment for topic")
    private String description;

    public static ScoreComment2 createNew() {
        return ScoreComment2.builder()
                .score(0)
                .description("")
                .build();
    }

    @Override
    public String stringValue() {
        return "`%s` - score is %s. %s".formatted("%s", (Math.round(score)), description);
    }

    @Override
    public int compareTo(ScoreComment2 other) {
        return Double.compare(other.score, this.score);
    }

}
