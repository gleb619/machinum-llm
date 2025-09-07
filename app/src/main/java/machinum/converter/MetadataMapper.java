package machinum.converter;

import machinum.config.MetadataProperties;
import machinum.service.TTSRestClient.Metadata;
import machinum.util.TextUtil;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.time.LocalDate;

@Mapper(componentModel = "spring")
public interface MetadataMapper {

    @AfterMapping
    static void convertNameToUpperCase(@MappingTarget Metadata metadata) {
        if (TextUtil.isEmpty(metadata.getTitle())) {
            metadata.setTitle("N/A");
        }
        if (TextUtil.isEmpty(metadata.getArtist())) {
            metadata.setArtist("N/A");
        }
        if (TextUtil.isEmpty(metadata.getAlbum())) {
            metadata.setAlbum("N/A");
        }
        if (TextUtil.isEmpty(metadata.getYear())) {
            metadata.setYear(String.valueOf(LocalDate.now().getYear()));
        }
        if (TextUtil.isEmpty(metadata.getGenre())) {
            metadata.setGenre("N/A");
        }
        if (TextUtil.isEmpty(metadata.getLanguage())) {
            metadata.setLanguage("rus");
        }
        if (TextUtil.isEmpty(metadata.getTrack())) {
            metadata.setTrack("N/A");
        }
        if (TextUtil.isEmpty(metadata.getPublisher())) {
            metadata.setPublisher("N/A");
        }
        if (TextUtil.isEmpty(metadata.getCopyright())) {
            metadata.setCopyright("N/A");
        }
        if (TextUtil.isEmpty(metadata.getComments())) {
            metadata.setComments("N/A");
        }
    }

    Metadata read(MetadataProperties value);

}
