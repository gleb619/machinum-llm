package machinum.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ConfigurationProperties("app.tts-service.metadata")
public class MetadataProperties {

    private String title;

    private String artist;

    private String album;

    private String year;

    private String genre;

    private String language;

    private String track;

    private String publisher;

    private String copyright;

    private String comments;

}
