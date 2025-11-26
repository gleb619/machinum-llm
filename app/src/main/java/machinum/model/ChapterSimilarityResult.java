package machinum.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import lombok.Value;

/**
 * Represents the result of a similarity search on chapter content.
 */
@Value
@Builder
public class ChapterSimilarityResult {
    /**
     * Cosine distance from query embedding (0.0 = identical, higher = less similar)
     */
    double distance;

    /**
     * Similarity score (0.0 = no similarity, 1.0 = identical)
     */
    double similarity;

    /**
     * The chapter context that matched
     */
    @JsonUnwrapped
    ChapterContext chapterContext;

    /**
     * Which field was matched (title, text, summary, etc.)
     */
    String matchedField;

    /**
     * The original content that was matched
     */
    String matchedContent;

}
