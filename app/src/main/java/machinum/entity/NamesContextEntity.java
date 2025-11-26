package machinum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@DynamicUpdate
@Table(name = "names_context")
public class NamesContextEntity {

    @Id
    @ToString.Include
    private String id; // Will be chapterId + sequential number, e.g., "abc1", "abc2"

    @ToString.Include
    @Column(name = "chapter_id", nullable = false)
    private String chapterId;

    @ToString.Include
    @Column(name = "name", columnDefinition = "TEXT", nullable = false)
    private String name;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "translated_name", columnDefinition = "TEXT")
    private String translatedName;

    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
