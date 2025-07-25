package machinum.model;


import lombok.*;
import machinum.service.AudioService.FileMetadata;

import java.time.Instant;
import java.util.function.Function;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AudioFile {

    private String id;

    private String chapterId;

    private String name;

    private AudioFileType type = AudioFileType.OTHER;

    private String minioKey;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    @ToString.Exclude
    private FileMetadata metadata = FileMetadata.createNew(Function.identity());

    public enum AudioFileType {

        SPEECH,
        BACKGROUND_SOUND,
        OTHER,

    }

}