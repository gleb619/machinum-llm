package machinum.entity;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "statistics_view")
public class StatisticsView {

    private String id;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "mode")
    private String mode;

    @Column(name = "run_id")
    private String runId;

    @Id
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

}
