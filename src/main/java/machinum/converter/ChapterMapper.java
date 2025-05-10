package machinum.converter;

import machinum.entity.ChapterEntity;
import machinum.model.Chapter;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChapterMapper extends BaseMapper<ChapterEntity, Chapter> {

}
