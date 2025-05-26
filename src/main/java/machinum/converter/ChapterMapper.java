package machinum.converter;

import machinum.entity.ChapterEntity;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.model.ChapterGlossary.ChapterGlossaryProjection;
import machinum.processor.core.ChapterWarning;
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

    ChapterGlossary toDto(ChapterGlossaryProjection projection);

}
