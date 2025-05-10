package machinum.config;

import machinum.service.plugin.StatisticPlugin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;

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

}
