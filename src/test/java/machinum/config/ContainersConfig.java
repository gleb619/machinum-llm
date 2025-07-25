package machinum.config;

import lombok.extern.slf4j.Slf4j;
import machinum.service.plugin.StatisticPlugin;
import org.awaitility.Awaitility;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Configuration
//@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    @Bean
    CommandLineRunner runner(StatisticPlugin statisticPlugin) {
        return args -> {
            statisticPlugin.init();
        };
    }

    @Bean
//    @RestartScope
    @ServiceConnection
    @ConditionalOnProperty(name = "spring.datasource.mode", havingValue = "test")
    PostgreSQLContainer<?> postgreSQLContainer(
            @Value("${spring.datasource.database-name}") String datasourceName,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword
    ) {
        return new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withUsername(datasourceUsername)
                .withPassword(datasourcePassword)
                .withDatabaseName(datasourceName);
    }

    @Bean
//    @ServiceConnection
    @ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
    MinIOContainer minioContainer(
            @Value("${app.minio.accessKey}") String accessKey,
            @Value("${app.minio.secretKey}") String secretKey
    ) {
        return new MinIOContainer("minio/minio:latest")
                .withEnv("MINIO_ACCESS_KEY", accessKey)
                .withEnv("MINIO_SECRET_KEY", secretKey);
    }

    // Most direct approach using BeanPostProcessor
    @Component
    @ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
    public class MinIOEnvironmentPostProcessor implements BeanPostProcessor, EnvironmentAware {

        private ConfigurableEnvironment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = (ConfigurableEnvironment) environment;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof MinIOContainer container) {
                if (!container.isRunning()) {
                    container.start();
                }

                CompletableFuture.runAsync(() -> {
                    Awaitility.await()
                            .atMost(Duration.ofSeconds(60))
                            .until(container::isRunning);

                    MutablePropertySources propertySources = environment.getPropertySources();
                    Properties dynamicProps = new Properties();
                    dynamicProps.setProperty("app.minio.endpoint", container.getS3URL());

                    PropertiesPropertySource propertySource = new PropertiesPropertySource("minioContainerProperties", dynamicProps);
                    propertySources.addFirst(propertySource);
                }).exceptionally(ex -> {
                    log.error("Got minio container error: ", ex);

                    return null;
                });
            }

            return bean;
        }
    }


}
