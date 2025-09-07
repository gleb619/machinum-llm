package machinum.config;

import machinum.service.plugin.StatisticPlugin;
import machinum.service.StatisticService;
import org.springframework.async.AsyncHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatisticConfig {

    @Bean
    public StatisticPlugin statisticPlugin(StatisticService statisticService, AsyncHelper asyncHelper) {
        return new StatisticPlugin(statisticService, asyncHelper);
    }

}
