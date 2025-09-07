package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.AudioFileEntity;
import machinum.model.AudioFile;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:18+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class AudioFileMapperImpl implements AudioFileMapper {

    @Override
    public List<AudioFileEntity> toEntity(List<AudioFile> list) {
        if ( list == null ) {
            return null;
        }

        List<AudioFileEntity> list1 = new ArrayList<AudioFileEntity>( list.size() );
        for ( AudioFile audioFile : list ) {
            list1.add( toEntity( audioFile ) );
        }

        return list1;
    }

    @Override
    public List<AudioFile> toDto(List<AudioFileEntity> value) {
        if ( value == null ) {
            return null;
        }

        List<AudioFile> list = new ArrayList<AudioFile>( value.size() );
        for ( AudioFileEntity audioFileEntity : value ) {
            list.add( toDto( audioFileEntity ) );
        }

        return list;
    }

    @Override
    public AudioFileEntity toEntity(AudioFile value) {
        if ( value == null ) {
            return null;
        }

        AudioFileEntity.AudioFileEntityBuilder audioFileEntity = AudioFileEntity.builder();

        audioFileEntity.id( value.getId() );
        audioFileEntity.chapterId( value.getChapterId() );
        audioFileEntity.name( value.getName() );
        if ( value.getType() != null ) {
            audioFileEntity.type( value.getType().name() );
        }
        audioFileEntity.minioKey( value.getMinioKey() );
        audioFileEntity.createdAt( value.getCreatedAt() );
        audioFileEntity.metadata( value.getMetadata() );

        return audioFileEntity.build();
    }

    @Override
    public AudioFile toDto(AudioFileEntity value) {
        if ( value == null ) {
            return null;
        }

        AudioFile.AudioFileBuilder audioFile = AudioFile.builder();

        audioFile.id( value.getId() );
        audioFile.chapterId( value.getChapterId() );
        audioFile.name( value.getName() );
        if ( value.getType() != null ) {
            audioFile.type( Enum.valueOf( AudioFile.AudioFileType.class, value.getType() ) );
        }
        audioFile.minioKey( value.getMinioKey() );
        audioFile.createdAt( value.getCreatedAt() );
        audioFile.metadata( value.getMetadata() );

        return audioFile.build();
    }
}
