package machinum.converter;

import machinum.entity.NamesContextEntity;
import machinum.model.NamesContext;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NamesContextMapper extends BaseMapper<NamesContextEntity, NamesContext> {

}
