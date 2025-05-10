package machinum.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Statistic {

    private String id;

    private LocalDate date;

    @Singular("item")
    private List<StatisticItem> data = new ArrayList<>();

    public Statistic addItem(StatisticItem item) {
        return toBuilder()
                .item(item.setPosition(data.size()))
                .build();
    }

    @Data
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class StatisticItem {

        private Integer position;
        private String mode;
        private String runId;
        private String operationName;
        private String operationType;
        private Integer chapter;
        private String rayId;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime operationDate;
        private Long operationTimeSeconds;
        private String operationTimeString;
        private Integer inputHistoryTokens;
        private Integer inputHistoryWords;
        private Integer inputTokens;
        private Integer inputWords;
        private Integer outputHistoryTokens;
        private Integer outputHistoryWords;
        private Integer outputTokens;
        private Integer outputWords;
        private Double conversionPercent;
        private Integer tokens;
        private Integer tokensLeft;
        private OllamaOptions aiOptions;

    }

}
