package machinum.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ChapterContext {

    @ToString.Include
    private String id; // Maps to chapter_info.id

    private String bookId;

    private Integer chapterNumber;

    // Title fields
    @ToString.Include
    private String titleContent;
    private float[] titleEmbedding;

    // Translated title fields
    @ToString.Include
    private String translatedTitleContent;
    private float[] translatedTitleEmbedding;

    // Text content fields
    private String textContent;
    private String textEmbedding;

    // Translated text fields
    private String translatedTextContent;
    private String translatedTextEmbedding;

    // Summary fields
    private String summaryContent;
    private float[] summaryEmbedding;

    @ToString.Include
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
