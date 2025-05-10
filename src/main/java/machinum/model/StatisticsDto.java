package machinum.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class StatisticsDto {

    private String id;
    private LocalDate date;
    private String mode;
    private String runId;
    private String operationName;
    private String operationType;
    private String chapter;
    private String rayId;
    private LocalDateTime operationDate;
    private Integer operationTimeSeconds;
    private String operationTimeString;
    private Integer inputHistoryTokens;
    private Integer inputHistoryWords;
    private Integer inputTokens;
    private Integer inputWords;
    private Integer outputHistoryTokens;
    private Integer outputHistoryWords;
    private Integer outputTokens;
    private Integer outputWords;
    private BigDecimal conversionPercent;
    private Integer tokens;
    private Integer tokensLeft;

}
