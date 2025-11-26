package machinum.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Represents the result of a similarity search on glossary names.
 */
@Value
@Builder
public class NameSimilarityResult {
    /**
     * Cosine distance from query embedding (0.0 = identical, higher = less similar)
     */
    double distance;

    /**
     * Similarity score (0.0 = no similarity, 1.0 = identical)
     */
    double similarity;

    /**
     * The object name that matched (frontend-compatible)
     */
    @JsonUnwrapped
    ObjectName objectName;

    /**
     * List of related names that are semantically similar (for consolidation analysis)
     */
    List<String> relatedNames;

    /**
     * Indicates if this result represents a potential duplicate that should be consolidated
     */
    boolean isPotentialDuplicate;
}
