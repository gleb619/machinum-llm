package machinum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity representing chapter contexts with embeddings.
 * This is a one-to-one relationship with ChapterEntity - each chapter has exactly one context record
 * containing embeddings for multiple fields (title, text, summary, etc.).
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@DynamicUpdate
@Table(name = "chapter_context")
public class ChapterContextEntity {

    @Id
    @ToString.Include
    @Column(name = "id", nullable = false)
    private String id; // Maps to chapter_info.id for one-to-one relationship

    @ToString.Include
    @Column(name = "book_id", nullable = false)
    private String bookId;

    @ToString.Include
    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    // Title fields
    @Column(name = "title_content", columnDefinition = "TEXT")
    private String titleContent;

    @Column(name = "title_embedding", columnDefinition = "vector(384)")
    private float[] titleEmbedding;

    // Translated title fields
    @Column(name = "translated_title_content", columnDefinition = "TEXT")
    private String translatedTitleContent;

    @Column(name = "translated_title_embedding", columnDefinition = "vector(384)")
    private float[] translatedTitleEmbedding;

    // Text content fields
    @Column(name = "text_content")
    @JdbcTypeCode(SqlTypes.JSON)
    private String textContent;

    @Column(name = "text_embedding")
    @JdbcTypeCode(SqlTypes.JSON)
    private String textEmbedding;

    // Translated text fields
    @Column(name = "translated_text_content")
    @JdbcTypeCode(SqlTypes.JSON)
    private String translatedTextContent;

    @Column(name = "translated_text_embedding")
    @JdbcTypeCode(SqlTypes.JSON)
    private String translatedTextEmbedding;

    // Summary fields
    @Column(name = "summary_content", columnDefinition = "TEXT")
    private String summaryContent;

    @Column(name = "summary_embedding", columnDefinition = "vector(384)")
    private float[] summaryEmbedding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
