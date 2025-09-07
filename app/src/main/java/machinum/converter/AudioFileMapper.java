package machinum.converter;

import machinum.entity.AudioFileEntity;
import machinum.model.AudioFile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AudioFileMapper extends BaseMapper<AudioFileEntity, AudioFile> {

}
