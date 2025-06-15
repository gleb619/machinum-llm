package machinum.entity;

import jakarta.persistence.*;
import lombok.*;
import machinum.model.Statistic.StatisticMessage;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Entity
@Table(name = "statistics")
public class StatisticEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    @Deprecated(forRemoval = true)
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "position")
    private Integer position;

    @Column(name = "mode")
    private String mode;

    @Column(name = "run_id")
    private String runId;

    @Column(name = "operation_name")
    private String operationName;

    @Column(name = "operation_type")
    private String operationType;

    @Column(name = "chapter")
    private String chapter;

    @Column(name = "ray_id")
    private String rayId;

    @Column(name = "operation_date")
    private LocalDateTime operationDate;

    @Column(name = "operation_time_seconds")
    private Integer operationTimeSeconds;

    @Column(name = "operation_time_string")
    private String operationTimeString;

    @Column(name = "input_history_tokens")
    private Integer inputHistoryTokens;

    @Column(name = "input_history_words")
    private Integer inputHistoryWords;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "input_words")
    private Integer inputWords;

    @Column(name = "output_history_tokens")
    private Integer outputHistoryTokens;

    @Column(name = "output_history_words")
    private Integer outputHistoryWords;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "output_words")
    private Integer outputWords;

    @Column(name = "conversion_percent")
    private BigDecimal conversionPercent;

    @Column(name = "tokens")
    private Integer tokens;

    @Column(name = "tokens_left")
    private Integer tokensLeft;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_options", columnDefinition = "jsonb")
    private OllamaOptions aiOptions = new OllamaOptions();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb")
    private List<StatisticMessage> messages = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
