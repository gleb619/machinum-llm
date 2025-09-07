package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.ChapterAnalysisMapper;
import machinum.model.ChapterDataSummary;
import machinum.model.ChapterDataSummary.ChapterHeatmapData;
import machinum.model.ChapterDataSummary.ChapterReadinessItem;
import machinum.repository.ChapterReportRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

import static machinum.config.Config.CacheConstants.CHAPTER_DATA_SUMMARY;
import static machinum.config.Config.CacheConstants.CHAPTER_HEATMAP_DATA;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterAnalysisService {

    private final ChapterReportRepository chapterReportRepository;
    private final ChapterAnalysisMapper chapterAnalysisMapper;


    @Transactional(readOnly = true)
    @Cacheable(CHAPTER_DATA_SUMMARY)
    public ChapterDataSummary getChapterDataSummary(String bookId) {
        log.info("Generating chapter data summary for bookId: {}", bookId);

        Long totalChapters = chapterReportRepository.countByBookId(bookId);

        if (totalChapters == 0) {
            return ChapterDataSummary.builder()
                    .bookId(bookId)
                    .totalChapters(0L)
                    .emptyTitles(0L)
                    .emptyTranslatedTitles(0L)
                    .emptyTexts(0L)
                    .emptyTranslatedTexts(0L)
                    .emptySummaries(0L)
                    .emptyNames(0L)
                    .emptyWarnings(0L)
                    .titleCompletionPercentage(0.0)
                    .translatedTitleCompletionPercentage(0.0)
                    .textCompletionPercentage(0.0)
                    .translatedTextCompletionPercentage(0.0)
                    .summaryCompletionPercentage(0.0)
                    .namesCompletionPercentage(0.0)
                    .warningsPercentage(0.0)
                    .build();
        }

        Long emptyTitles = chapterReportRepository.countEmptyTitlesByBookId(bookId);
        Long emptyTranslatedTitles = chapterReportRepository.countEmptyTranslatedTitlesByBookId(bookId);
        Long emptyTexts = chapterReportRepository.countEmptyTextsByBookId(bookId);
        Long emptyTranslatedTexts = chapterReportRepository.countEmptyTranslatedTextsByBookId(bookId);
        Long emptySummaries = chapterReportRepository.countEmptySummariesByBookId(bookId);
        Long emptyNames = chapterReportRepository.countEmptyNamesByBookId(bookId);
        Long emptyTranslatedNames = chapterReportRepository.countTranslatedNamesByBookId(bookId);
        Long emptyWarnings = chapterReportRepository.countEmptyWarningsByBookId(bookId);

        // Calculate completion percentages
        Double titleCompletionPercentage = ((double) (totalChapters - emptyTitles) / totalChapters) * 100;
        Double translatedTitleCompletionPercentage = ((double) (totalChapters - emptyTranslatedTitles) / totalChapters) * 100;
        Double textCompletionPercentage = ((double) (totalChapters - emptyTexts) / totalChapters) * 100;
        Double translatedTextCompletionPercentage = ((double) (totalChapters - emptyTranslatedTexts) / totalChapters) * 100;
        Double summaryCompletionPercentage = ((double) (totalChapters - emptySummaries) / totalChapters) * 100;
        Double namesCompletionPercentage = ((double) (totalChapters - emptyNames) / totalChapters) * 100;
        Double translatedNamesCompletionPercentage = ((double) (totalChapters - emptyTranslatedNames) / totalChapters) * 100;
        Double warningsPercentage = ((double) (totalChapters - emptyWarnings) / totalChapters) * 100;

        return ChapterDataSummary.builder()
                .bookId(bookId)
                .totalChapters(totalChapters)
                .emptyTitles(emptyTitles)
                .emptyTranslatedTitles(emptyTranslatedTitles)
                .emptyTexts(emptyTexts)
                .emptyTranslatedTexts(emptyTranslatedTexts)
                .emptySummaries(emptySummaries)
                .emptyNames(emptyNames)
                .emptyTranslatedNames(emptyTranslatedNames)
                .emptyWarnings(emptyWarnings)
                .titleCompletionPercentage(titleCompletionPercentage)
                .translatedTitleCompletionPercentage(translatedTitleCompletionPercentage)
                .textCompletionPercentage(textCompletionPercentage)
                .translatedTextCompletionPercentage(translatedTextCompletionPercentage)
                .summaryCompletionPercentage(summaryCompletionPercentage)
                .namesCompletionPercentage(namesCompletionPercentage)
                .translatedNamesCompletionPercentage(translatedNamesCompletionPercentage)
                .warningsPercentage(warningsPercentage)
                .build();
    }

    @CacheEvict(CHAPTER_HEATMAP_DATA)
    public void clearHeatmapCache(String bookId) {
        log.debug("Will clean heatmap cache due user request: {}", bookId);
    }

    @Transactional(readOnly = true)
    @Cacheable(CHAPTER_HEATMAP_DATA)
    public ChapterHeatmapData getChapterHeatmapData(String bookId) {
        log.info("Generating chapter heatmap data for bookId: {}", bookId);

        var chapters = chapterReportRepository.getChapterReadinessData(bookId).stream()
                .map(chapterAnalysisMapper::toDto)
                .collect(Collectors.toList());

        // Calculate statistics
        var averageReadiness = chapters.stream()
                .mapToDouble(ChapterReadinessItem::getReadinessIndex)
                .average()
                .orElse(0.0);

        var statusCounts = chapters.stream()
                .collect(Collectors.groupingBy(
                        ChapterReadinessItem::getStatus,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)));

        return new ChapterHeatmapData(bookId, chapters, averageReadiness, statusCounts, chapters.size());
    }

}
