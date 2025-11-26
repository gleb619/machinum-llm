package machinum.model;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a semantic similarity relationship between names in a book glossary.
 * Used to identify potential typos, spelling variations, or related terms.
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class NameSimilarity {

    /**
     * The target name this similarity points to (the "canonical" or "trusted" version)
     */
    private String name;

    /**
     * Chapter number where the target name appears
     */
    private Integer chapterNumber;

    /**
     * Unique identifier of the chapter containing the target name
     */
    private String chapterId;

    /**
     * Similarity confidence score (0.0 to 1.0)
     * Higher scores indicate stronger likelihood of being the same entity
     */
    private Double confidence;

    /**
     * Trust level indicating the reliability of this similarity relationship
     */
    private TrustLevel trustLevel;

    /**
     * Human-readable explanation of why these names are similar
     */
    private String reason;

    /**
     * When this similarity was last updated (for temporal reasoning)
     */
    private LocalDateTime lastUpdated;

    /**
     * Creates a new similarity relation between two names
     */
    @Deprecated(forRemoval = true)
    public static NameSimilarity between(ObjectName name1, ObjectName name2,
                                         double confidence, TrustLevel trust,
                                         String reasoning) {
        return NameSimilarity.builder()
                .name(name1.getName())
                .chapterNumber(0) // TODO: Add chapter context when available
                .confidence(confidence)
                .trustLevel(trust)
                .reason(reasoning)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public enum TrustLevel {
        /**
         * Name appears in previous, verified chapters and has been manually confirmed
         */
        CONFIRMED,

        /**
         * Name appears in previous chapters but hasn't been explicitly verified
         * (can be auto-assigned based on chapter progression)
         */
        SEMI_TRUSTED,

        /**
         * AI-suggested similarity based on embedding analysis
         * Requires manual verification before use
         */
        SUGGESTED
    }
}
