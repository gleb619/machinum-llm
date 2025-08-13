package machinum.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRowGenerator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.ChapterHistoryMapper.ChapterInfoHistoryConverter;
import machinum.converter.JsonlConverter;
import machinum.extract.util.PatchDeserializer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheHelper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.CachePlugin;
import org.springframework.cache.InMemoryCache;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.plugin.PluginConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.db.DbHelper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryHelper;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.CustomRequestLoggingFilter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static machinum.config.Config.CacheConstants.*;

@Slf4j
@Configuration
@EnableCaching
@EnableRetry
@Import({PluginConfig.class})
public class Config {

    @Bean
    VectorStore vectorStore(JdbcTemplate template, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(template, embeddingModel)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    @Bean
    public JsonlConverter jsonlConverter(ObjectMapper objectMapper) {
        return new JsonlConverter(new TypeReference<>() {
        }, objectMapper);
    }

    @Bean
    public Caffeine caffeineConfig() {
        return Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.DAYS);
    }

    @Bean
    public CacheManager cacheManager(Caffeine caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        caffeineCacheManager.setCacheNames(List.of(STORE, BOOKS_FOR_EXPORT, CHAPTER_DATA_SUMMARY, CHAPTER_HEATMAP_DATA));
        return caffeineCacheManager;
    }

    @Bean
    public InMemoryCache<String, Object> inMemoryCache() {
        return InMemoryCache.create(Duration.ofMinutes(60));
    }

    @Bean
    public CacheHelper cacheHelper(CacheManager cacheManager, List<CachePlugin> plugins) {
        return new CacheHelper(cacheManager, plugins);
    }

    @Bean
    public RetryHelper retryHelper() {
        return new RetryHelper();
    }

    @Bean
    public DbHelper dbHelper(VectorStore vectorStore, ApplicationContext context) {
        return new DbHelper(vectorStore, context);
    }

    @Bean
    public AsyncHelper asyncHelper() {
        return new AsyncHelper();
    }

    @Bean
    public ChapterInfoHistoryConverter chapterInfoHistoryConverter(@Qualifier("objectMapperHolder") Holder<ObjectMapper> holder) {
        ObjectMapper mapper = holder.copy();
        mapper.registerModule(diffUtilsModule());

        return new ChapterInfoHistoryConverter(mapper);
    }

    @Bean
    public SimpleModule diffUtilsModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Patch.class, new PatchDeserializer());

        return module;
    }

    @Bean
    //TODO rewrite, add status to response
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CustomRequestLoggingFilter loggingFilter = new CustomRequestLoggingFilter("< ");
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeHeaders(false);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludePayload(true);
        loggingFilter.setMaxPayloadLength(6400);
        loggingFilter.setBeforeMessagePrefix("> ");
        loggingFilter.setAfterMessagePrefix("< ");

        return loggingFilter;
    }

    @Bean
    public DiffRowGenerator diffRowGenerator() {
        return DiffRowGenerator.create()
                .showInlineDiffs(true)
                .inlineDiffByWord(true)
                .oldTag(f -> "~~")      //introduce markdown style for strikethrough
                .newTag(f -> "**")     //introduce markdown style for bold
                .processDiffs(s -> {
                    if (!s.isBlank()) {
                        return s.trim();
                    } else {
                        return s;
                    }
                })
                .build();
    }

    @Bean
    public Holder<ObjectMapper> linesMapper(Jackson2ObjectMapperBuilder builder) {
        return Holder.of(builder
                .createXmlMapper(false)
                .featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CacheConstants {

        public static final String STORE = "store";

        public static final String BOOKS_FOR_EXPORT = "booksForExport";

        public static final String CHAPTER_DATA_SUMMARY = "chapterDataSummary";

        public static final String CHAPTER_HEATMAP_DATA = "chapterHeatmapData";

    }

    @Configuration
    public static class CacheExpiry {

        @Scheduled(fixedRateString = "PT60S")
        @CacheEvict(value = {
                CHAPTER_DATA_SUMMARY,
                CHAPTER_HEATMAP_DATA
        }, allEntries = true)
        public void emptyCaches() {
            log.trace("Emptying cache...");
        }

    }

}