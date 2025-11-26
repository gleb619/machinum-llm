package machinum.util;

import lombok.experimental.UtilityClass;

/**
 * Utility class for embedding-related calculations and operations.
 */
@UtilityClass
public class EmbeddingUtils {

    /**
     * Calculate cosine distance between two embedding vectors.
     * Cosine distance = 1 - cosine similarity
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine distance between 0.0 (identical) and 2.0 (opposite)
     */
    public static double cosineDistance(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != embedding2.length) {
            return 1.0; // Maximum distance for invalid inputs
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 1.0; // Avoid division by zero
        }

        double cosineSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));

        // Clamp to [-1, 1] range to handle floating point precision issues
        cosineSimilarity = Math.max(-1.0, Math.min(1.0, cosineSimilarity));

        // Convert to distance: 0 = identical, 2 = opposite
        return 1.0 - cosineSimilarity;
    }

    /**
     * Calculate cosine similarity between two embedding vectors.
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity between -1.0 and 1.0
     */
    public static double cosineSimilarity(float[] embedding1, float[] embedding2) {
        double distance = cosineDistance(embedding1, embedding2);
        return 1.0 - distance;
    }
}
