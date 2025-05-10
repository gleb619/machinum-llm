package machinum.entity;

import machinum.listener.ChapterEntityListener;
import machinum.model.*;
import machinum.model.Character;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.id.UUIDGenerator;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@DynamicUpdate
@Table(name = "chapter_info")
@EntityListeners(ChapterEntityListener.class)
public class ChapterEntity {

    @Id
    @ToString.Include
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", type = UUIDGenerator.class)
    private String id;

    @ToString.Include
    @Column(updatable = false)
    private String bookId;

    @ToString.Include
    private String sourceKey;

    @ToString.Include
    private Integer number;

    @ToString.Include
    private String title;

    private String translatedTitle;

    @Deprecated
    private String rawText;

    private String text;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Chunks cleanChunks = Chunks.createNew();

    private String proofreadText;

    private String translatedText;

//    private String fixedTranslatedText;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Chunks translatedChunks = Chunks.createNew();

//    @Builder.Default
//    @JdbcTypeCode(SqlTypes.JSON)
//    @Column(columnDefinition = "json")
//    private Chunks fixedTranslatedChunks = Chunks.createNew();

    private String summary;

    private String consolidatedSummary;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> keywords = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private ChainOfThoughts selfConsistency = ChainOfThoughts.createNew();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> quotes = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Character> characters = new ArrayList<>();

    private String themes;

    private String perspective;

    private String tone;

    private String foreshadowing;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<ObjectName> names = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Scene> scenes = new ArrayList<>();

}