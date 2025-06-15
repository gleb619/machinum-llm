package machinum.converter;

import machinum.entity.ChapterEntity;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.model.ChapterGlossary.ChapterGlossaryProjection;
import machinum.processor.core.ChapterWarning;
import machinum.repository.ChapterRepository.ChapterTitleDto;
import org.mapstruct.Mapper;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import static machinum.processor.core.ChapterWarning.WarningType.EMPTY_FIELD;
import static machinum.util.JavaUtil.firstNotNull;
import static machinum.util.TextUtil.length;

@Mapper(componentModel = "spring")
public interface ChapterMapper extends BaseMapper<ChapterEntity, Chapter> {

    default Chapter checkForWarnings(Chapter source) {
        var target = source.toBuilder()
                .warnings(new ArrayList<>(source.getWarnings()))
                .build();
        var list = new HashSet<>(firstNotNull(target.getWarnings(), Collections.emptyList()));

        if (length(target.getTitle()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Title can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "title")
            ));
        }
        if (length(target.getTranslatedTitle()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Translated title can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "translatedTitle")
            ));
        }
        if (length(target.getText()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Original text can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "text")
            ));
        }
        if (length(target.getTranslatedText()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Translated text can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "translatedText")
            ));
        }
        if (length(target.getSummary()) < 2) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Summary can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "summary")
            ));
        }
        if (CollectionUtils.isEmpty(target.getNames())) {
            list.add(ChapterWarning.createNew(b -> b
                    .type(EMPTY_FIELD)
                    .text("Glossary can't be empty")
                    .metadata(ChapterWarning.NAME_PARAM, "names")
            ));
        }

        target.setWarnings(new ArrayList<>(list));

        return target;
    }

    Chapter toDto(ChapterTitleDto chapterTitleDto);

    ChapterGlossary toDto(ChapterGlossaryProjection projection);

}
