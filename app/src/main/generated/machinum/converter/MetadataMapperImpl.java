package machinum.converter;

import javax.annotation.processing.Generated;
import machinum.config.MetadataProperties;
import machinum.service.TTSRestClient;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:17+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class MetadataMapperImpl implements MetadataMapper {

    @Override
    public TTSRestClient.Metadata read(MetadataProperties value) {
        if ( value == null ) {
            return null;
        }

        TTSRestClient.Metadata.MetadataBuilder metadata = TTSRestClient.Metadata.builder();

        metadata.title( value.getTitle() );
        metadata.artist( value.getArtist() );
        metadata.album( value.getAlbum() );
        metadata.year( value.getYear() );
        metadata.genre( value.getGenre() );
        metadata.language( value.getLanguage() );
        metadata.track( value.getTrack() );
        metadata.publisher( value.getPublisher() );
        metadata.copyright( value.getCopyright() );
        metadata.comments( value.getComments() );

        TTSRestClient.Metadata metadataResult = metadata.build();

        MetadataMapper.convertNameToUpperCase( metadataResult );

        return metadataResult;
    }
}
