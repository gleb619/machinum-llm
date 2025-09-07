package machinum.entity;

import com.fasterxml.jackson.databind.JsonNode;
import machinum.listener.ChapterHistoryEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.id.UUIDGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@DynamicUpdate
@Table(name = "chapter_info_history")
@EntityListeners(ChapterHistoryEntityListener.class)
public class ChapterHistoryEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", type = UUIDGenerator.class)
    private String id;

    private String chapterInfoId;

    @Builder.Default
    private Integer number = 0;

    private String fieldName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private JsonNode patch;

    private LocalDateTime createdAt;

}
