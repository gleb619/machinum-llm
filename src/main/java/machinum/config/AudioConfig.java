package machinum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.MetadataMapper;
import machinum.service.TTSRestClient.Metadata;
import machinum.util.TextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import static machinum.config.Holder.of;

@Slf4j
@Configuration
@EnableConfigurationProperties(MetadataProperties.class)
public class AudioConfig {

    @Bean
    public Holder<ObjectMapper> ttsObjectMapper(Jackson2ObjectMapperBuilder builder) {
        return AIConfig.createMapper(builder, b -> b
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
    }

    @Bean
    public Holder<HttpClient> ttsHttpClient() {
        return of(HttpClient.newBuilder()
                //10min
                .connectTimeout(Duration.ofSeconds(600))
                .build());
    }

    @Bean
    public Holder<HttpClient> minioHttpClient() {
        return of(HttpClient.newBuilder()
                //10min
                .connectTimeout(Duration.ofSeconds(600))
                .build());
    }

    @Bean
    public MinioClient minioClient(@Value("${app.minio.endpoint}") String minioEndpoint,
                                   @Value("${app.minio.accessKey}") String minioAccessKey,
                                   @Value("${app.minio.secretKey}") String minioSecretKey) {
        return MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    @Bean
    public Metadata chapterMetadata(MetadataProperties properties, MetadataMapper metadataMapper) {
        var metadata = metadataMapper.read(properties);
        var copyright = TextUtil.isNotEmpty(metadata.getCopyright()) ? metadata.getCopyright() : "";
        var year = TextUtil.isNotEmpty(metadata.getYear()) ? metadata.getYear() : "";
        var now = LocalDate.now().getYear() + "";

        return metadata.toBuilder()
                .copyright(copyright.replace("{year}", now))
                .year(year.replace("{year}", now))
                .build();
    }

}