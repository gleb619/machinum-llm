package machinum.model;

import lombok.*;
import machinum.processor.core.ChapterWarning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ChapterDataSummary {

    private String bookId;
    private Long totalChapters;
    private Long emptyTitles;
    private Long emptyTranslatedTitles;
    private Long emptyTexts;
    private Long emptyTranslatedTexts;
    private Long emptySummaries;
    private Long emptyNames;
    private Long emptyWarnings;
    private Double titleCompletionPercentage;
    private Double translatedTitleCompletionPercentage;
    private Double textCompletionPercentage;
    private Double translatedTextCompletionPercentage;
    private Double summaryCompletionPercentage;
    private Double namesCompletionPercentage;
    private Double warningsPercentage;

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ChapterHeatmapData {

        private String bookId;
        @Builder.Default
        private List<ChapterReadinessItem> chapters = new ArrayList<>();
        private Double averageReadiness;
        @Builder.Default
        private Map<String, Integer> statusCounts = new HashMap<>();
        private Integer totalChapters;

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ChapterReadinessItem {

        private String id;
        private Integer chapterNumber;
        private Double readinessIndex;
        private String status;
        private Boolean title;
        private Boolean translatedTitle;
        private Boolean text;
        private Boolean translatedText;
        private Boolean summary;
        private Boolean names;
        private Boolean warnings;
        @Builder.Default
        private List<ChapterWarning> chapterWarnings = new ArrayList<>();

    }


}
