package machinum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.Immutable;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Immutable
@Entity
@Table(name = "lines_info")
public class LineView {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "chapter_id", nullable = false, updatable = false)
    private String chapterId;

    @Column(name = "source_key", nullable = false, updatable = false)
    private String sourceKey;

    @Column(name = "number", nullable = false, updatable = false)
    private Integer number;

    @Column(name = "book_id", nullable = false, updatable = false)
    private String bookId;

    @Column(name = "line_index", nullable = false, updatable = false)
    private Integer lineIndex;

    @Column(name = "original_line", updatable = false)
    private String originalLine;

    @Column(name = "translated_line", updatable = false)
    private String translatedLine;

}
