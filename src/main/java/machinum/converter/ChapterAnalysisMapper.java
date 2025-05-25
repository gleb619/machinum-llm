package machinum.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.config.Holder;
import machinum.model.ChapterDataSummary.ChapterReadinessItem;
import machinum.processor.core.ChapterWarning;
import machinum.repository.ChapterReportRepository.ChapterReadinessItemProjection;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ChapterAnalysisMapper {

    @Autowired
    private Holder<ObjectMapper> objectMapperHolder;

    public ChapterReadinessItem toDto(ChapterReadinessItemProjection projection) {
        var builder = ChapterReadinessItem.builder()
                .id(projection.getId())
                .chapterNumber(projection.getNumber())
                .chapterWarnings(objectMapperHolder.execute(mapper ->
                        List.of(mapper.readValue(projection.getWarningsRaw(), ChapterWarning[].class))));

        // Field completion tracking
        boolean hasTitle = projection.getTitle() != null && !projection.getTitle().trim().isEmpty();
        boolean hasTranslatedTitle = projection.getTranslatedTitle() != null && !projection.getTranslatedTitle().trim().isEmpty();
        boolean hasText = projection.getText() != null && !projection.getText().trim().isEmpty();
        boolean hasTranslatedText = projection.getTranslatedText() != null && !projection.getTranslatedText().trim().isEmpty();
        boolean hasSummary = projection.getSummary() != null && !projection.getSummary().trim().isEmpty();
        boolean hasNames = projection.getNameCount() != null && projection.getNameCount() > 0;
        boolean hasNotWarnings = projection.getWarningCount() == null || projection.getWarningCount() == 0;

        builder.title(hasTitle)
                .translatedTitle(hasTranslatedTitle)
                .text(hasText)
                .translatedText(hasTranslatedText)
                .summary(hasSummary)
                .names(hasNames)
                .warnings(hasNotWarnings);

        // Calculate readiness index (0-100)
        double score = 0;

        // Core content (50% weight)
        if (hasTitle) score += 15;
        if (hasText) score += 20;
        if (hasSummary) score += 15;

        // Translation (35% weight)
        if (hasTranslatedTitle) score += 15;
        if (hasTranslatedText) score += 20;

        // Metadata (15% weight)
        if (hasNames) score += 10;
        if (hasNotWarnings) score += 5;

        builder.readinessIndex(score);

        // Determine status
        if (score >= 90) builder.status("excellent");
        else if (score >= 70) builder.status("good");
        else if (score >= 50) builder.status("fair");
        else if (score >= 30) builder.status("poor");
        else builder.status("critical");

        return builder.build();
    }

}
