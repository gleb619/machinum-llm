package machinum.repository;

import machinum.entity.ChapterContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterContextRepository extends JpaRepository<ChapterContextEntity, String> {

    // Search chapter content by specific field embedding
    @Query(value = """
            SELECT cc.*,
                   CASE :fieldType
                        WHEN 'title' THEN title_embedding <=> cast(:embedding as vector(384))
                        WHEN 'translated_title' THEN translated_title_embedding <=> cast(:embedding as vector(384))
                        WHEN 'text' THEN text_embedding <=> cast(:embedding as vector(384))
                        WHEN 'translated_text' THEN translated_text_embedding <=> cast(:embedding as vector(384))
                        WHEN 'summary' THEN summary_embedding <=> cast(:embedding as vector(384))
                        ELSE title_embedding <=> cast(:embedding as vector(384))
                   END as distance,
                   :fieldType as matched_field
            FROM chapter_context cc
            WHERE book_id = :bookId
              AND (
                  (:fieldType = 'title' AND title_embedding <=> cast(:embedding as vector(384)) < :threshold)
                  OR (:fieldType = 'translated_title' AND translated_title_embedding <=> cast(:embedding as vector(384)) < :threshold)
                  OR (:fieldType = 'text' AND text_embedding <=> cast(:embedding as vector(384)) < :threshold)
                  OR (:fieldType = 'translated_text' AND translated_text_embedding <=> cast(:embedding as vector(384)) < :threshold)
                  OR (:fieldType = 'summary' AND summary_embedding <=> cast(:embedding as vector(384)) < :threshold)
              )
            ORDER BY CASE :fieldType
                        WHEN 'title' THEN title_embedding <=> cast(:embedding as vector(384))
                        WHEN 'translated_title' THEN translated_title_embedding <=> cast(:embedding as vector(384))
                        WHEN 'text' THEN text_embedding <=> cast(:embedding as vector(384))
                        WHEN 'translated_text' THEN translated_text_embedding <=> cast(:embedding as vector(384))
                        WHEN 'summary' THEN summary_embedding <=> cast(:embedding as vector(384))
                        ELSE title_embedding <=> cast(:embedding as vector(384))
                     END
            LIMIT :limit
            """, nativeQuery = true)
    List<ChapterSimilarityProjection> findSimilarByFieldProjected(@Param("bookId") String bookId,
                                                                  @Param("embedding") String embedding,
                                                                  @Param("fieldType") String fieldType,
                                                                  @Param("threshold") double threshold,
                                                                  @Param("limit") int limit);

    // Cross-field search to find chapters with similar content across any field
    @Query(value = """
            SELECT cc.*,
                   LEAST(
                       title_embedding <=> cast(:embedding as vector(384)),
                       translated_title_embedding <=> cast(:embedding as vector(384)),
                       text_embedding <=> cast(:embedding as vector(384)),
                       translated_text_embedding <=> cast(:embedding as vector(384)),
                       summary_embedding <=> cast(:embedding as vector(384))
                   ) as distance,
                   CASE WHEN title_embedding <=> cast(:embedding as vector(384)) = LEAST(
                       title_embedding <=> cast(:embedding as vector(384)),
                       translated_title_embedding <=> cast(:embedding as vector(384)),
                       text_embedding <=> cast(:embedding as vector(384)),
                       translated_text_embedding <=> cast(:embedding as vector(384)),
                       summary_embedding <=> cast(:embedding as vector(384))
                   ) THEN 'title'
                        WHEN translated_title_embedding <=> cast(:embedding as vector(384)) = LEAST(
                       title_embedding <=> cast(:embedding as vector(384)),
                       translated_title_embedding <=> cast(:embedding as vector(384)),
                       text_embedding <=> cast(:embedding as vector(384)),
                       translated_text_embedding <=> cast(:embedding as vector(384)),
                       summary_embedding <=> cast(:embedding as vector(384))
                   ) THEN 'translated_title'
                        WHEN text_embedding <=> cast(:embedding as vector(384)) = LEAST(
                       title_embedding <=> cast(:embedding as vector(384)),
                       translated_title_embedding <=> cast(:embedding as vector(384)),
                       text_embedding <=> cast(:embedding as vector(384)),
                       translated_text_embedding <=> cast(:embedding as vector(384)),
                       summary_embedding <=> cast(:embedding as vector(384))
                   ) THEN 'text'
                        WHEN translated_text_embedding <=> cast(:embedding as vector(384)) = LEAST(
                       title_embedding <=> cast(:embedding as vector(384)),
                       translated_title_embedding <=> cast(:embedding as vector(384)),
                       text_embedding <=> cast(:embedding as vector(384)),
                       translated_text_embedding <=> cast(:embedding as vector(384)),
                       summary_embedding <=> cast(:embedding as vector(384))
                   ) THEN 'translated_text'
                        WHEN summary_embedding <=> cast(:embedding as vector(384)) = LEAST(
                       title_embedding <=> cast(:embedding as vector(384)),
                       translated_title_embedding <=> cast(:embedding as vector(384)),
                       text_embedding <=> cast(:embedding as vector(384)),
                       translated_text_embedding <=> cast(:embedding as vector(384)),
                       summary_embedding <=> cast(:embedding as vector(384))
                   ) THEN 'summary'
                        ELSE 'unknown'
                   END as matched_field
            FROM chapter_context cc
            WHERE book_id = :bookId
              AND (title_embedding <=> cast(:embedding as vector(384)) < :threshold
                   OR translated_title_embedding <=> cast(:embedding as vector(384)) < :threshold
                   OR text_embedding <=> cast(:embedding as vector(384)) < :threshold
                   OR translated_text_embedding <=> cast(:embedding as vector(384)) < :threshold
                   OR summary_embedding <=> cast(:embedding as vector(384)) < :threshold)
            ORDER BY LEAST(
                        title_embedding <=> cast(:embedding as vector(384)),
                        translated_title_embedding <=> cast(:embedding as vector(384)),
                        text_embedding <=> cast(:embedding as vector(384)),
                        translated_text_embedding <=> cast(:embedding as vector(384)),
                        summary_embedding <=> cast(:embedding as vector(384))
                    )
            LIMIT :limit
            """, nativeQuery = true)
    List<ChapterSimilarityProjection> findSimilarAcrossFieldsProjected(@Param("bookId") String bookId,
                                                                       @Param("embedding") String embedding,
                                                                       @Param("threshold") double threshold,
                                                                       @Param("limit") int limit);

    /**
     * Projection interface for chapter similarity queries to avoid Object[] casting.
     */
    interface ChapterSimilarityProjection {
        ChapterContextEntity getEntity();

        Double getDistance();

        String getMatchedField();
    }


}
