package machinum.converter;

import machinum.entity.ChapterGlossaryView;
import machinum.model.ChapterGlossary;
import machinum.model.ChapterGlossary.ChapterGlossaryProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {ObjectNameMapper.class})
public interface ChapterGlossaryMapper extends BaseMapper<ChapterGlossaryView, ChapterGlossary> {

    ChapterGlossary toDto(ChapterGlossaryProjection projection);

    @Override
    @Mapping(target = "objectName", source = "view")
    @Mapping(target = "chapterNumber", source = "number")
    ChapterGlossary toDto(ChapterGlossaryView view);

    @Override
    @Mapping(target = "name", source = "dto.objectName.name")
    @Mapping(target = "category", source = "dto.objectName.category")
    @Mapping(target = "description", source = "dto.objectName.description")
    ChapterGlossaryView toEntity(ChapterGlossary dto);

}
