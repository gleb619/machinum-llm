package machinum.model;

import lombok.*;
import org.springframework.ai.chat.messages.Message;
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

    @Deprecated(forRemoval = true)
    private LocalDate date;

    private Integer position = 0;

    private String mode = "production";

    private String runId;

    private String operationName;

    private String operationType;

    private Integer chapter = -1;

    private String rayId;

    @Builder.Default
    private LocalDateTime operationDate = LocalDateTime.now();

    @Builder.Default
    private Long operationTimeSeconds = 0L;

    @Builder.Default
    private String operationTimeString = "0s";

    @Builder.Default
    private Integer inputHistoryTokens = 0;

    @Builder.Default
    private Integer inputHistoryWords = 0;

    @Builder.Default
    private Integer inputTokens = 0;

    @Builder.Default
    private Integer inputWords = 0;

    @Builder.Default
    private Integer outputHistoryTokens = 0;

    @Builder.Default
    private Integer outputHistoryWords = 0;

    @Builder.Default
    private Integer outputTokens = 0;

    @Builder.Default
    private Integer outputWords = 0;

    @Builder.Default
    private Double conversionPercent = 100d;

    @Builder.Default
    private Integer tokens = 0;

    @Builder.Default
    private Integer tokensLeft = 0;

    private OllamaOptions aiOptions;

    @Singular("message")
    private List<StatisticMessage> messages = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class StatisticMessage {

        @Builder.Default
        private String type = "message";
        @Builder.Default
        private String text = "";

        public static StatisticMessage of(Message message) {
            return StatisticMessage.builder()
                    .type(message.getMessageType().getValue())
                    .text(message.getText())
                    .build();
        }

    }

}
