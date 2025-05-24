package machinum.converter;

import machinum.entity.ChapterEntity;
import machinum.model.Chapter;
import machinum.model.ChapterDataSummary.ChapterReadinessItem;
import machinum.processor.core.ChapterWarning;
import machinum.repository.ChapterReportRepository.ChapterReadinessItemProjection;
import machinum.repository.ChapterRepository.ChapterTitleDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import static machinum.processor.core.ChapterWarning.WarningType.EMPTY_FIELD;
import static machinum.util.JavaUtil.firstNotNull;
import static machinum.util.TextUtil.length;

@Mapper(componentModel = "spring")
public interface ChapterMapper extends BaseMapper<ChapterEntity, Chapter> {

    @AfterMapping
    default void checkForWarnings(ChapterEntity source, @MappingTarget Chapter target) {
        var list = new HashSet<>(firstNotNull(source.getWarnings(), Collections.emptyList()));
        if(length(source.getTitle()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Title can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "title")
            ));
        }
        if(length(source.getTranslatedTitle()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Translated title can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "translatedTitle")
            ));
        }
        if(length(source.getText()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Original text can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "text")
            ));
        }
        if(length(source.getTranslatedText()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Translated text can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "translatedText")
            ));
        }
        if(length(source.getSummary()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Summary can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "summary")
            ));
        }
        if(CollectionUtils.isEmpty(source.getNames())) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Glossary can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "names")
            ));
        }

        target.setWarnings(new ArrayList<>(list));
    }

    Chapter toDto(ChapterTitleDto chapterTitleDto);

    default ChapterReadinessItem toDto(ChapterReadinessItemProjection projection) {
        var builder = ChapterReadinessItem.builder()
                .chapterNumber(projection.getNumber());

        // Field completion tracking
        boolean hasTitle = projection.getTitle() != null && !projection.getTitle().trim().isEmpty();
        boolean hasTranslatedTitle = projection.getTranslatedTitle() != null && !projection.getTranslatedTitle().trim().isEmpty();
        boolean hasText = projection.getText() != null && !projection.getText().trim().isEmpty();
        boolean hasTranslatedText = projection.getTranslatedText() != null && !projection.getTranslatedText().trim().isEmpty();
        boolean hasSummary = projection.getSummary() != null && !projection.getSummary().trim().isEmpty();
        boolean hasNames = projection.getNameCount() != null && projection.getNameCount() > 0;
        boolean hasWarnings = projection.getWarningCount() != null && projection.getWarningCount() > 0;

        builder.title(hasTitle)
                .translatedTitle(hasTranslatedTitle)
                .text(hasText)
                .translatedText(hasTranslatedText)
                .summary(hasSummary)
                .names(hasNames)
                .warnings(hasWarnings);

        // Calculate readiness index (0-100)
        double score = 0;

        // Core content (60% weight)
        if (hasTitle) score += 15;
        if (hasText) score += 25;
        if (hasSummary) score += 20;

        // Translation (25% weight)
        if (hasTranslatedTitle) score += 10;
        if (hasTranslatedText) score += 15;

        // Metadata (15% weight)
        if (hasNames) score += 10;
        if (hasWarnings) score += 5;

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
