package machinum.converter;

import machinum.entity.ChapterContextEntity;
import machinum.model.ChapterContext;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChapterContextMapper extends BaseMapper<ChapterContextEntity, ChapterContext> {

}
