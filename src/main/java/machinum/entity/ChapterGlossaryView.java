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
@Table(name = "chapter_glossary", schema = "public")
public class ChapterGlossaryView {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "chapter_id", nullable = false)
    private String chapterId;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "book_id", nullable = false)
    private String bookId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "translated", nullable = false)
    private Boolean translated;

    @Column(name = "translated")
    private String translatedName;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(name = "search_string1", columnDefinition = "tsvector")
    private String searchString1;

    @Column(name = "search_string2", columnDefinition = "tsvector")
    private String searchString2;

    @Column(name = "search_string3", columnDefinition = "text")
    private String searchString3;

}
