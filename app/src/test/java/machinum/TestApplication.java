package machinum;

import machinum.config.ContainersConfig;
import machinum.service.plugin.StatisticPlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        var context = SpringApplication.from(LLMApplication::main)
                .with(ContainersConfig.class)
                .run(args).getApplicationContext();

        context.getBean(StatisticPlugin.class).init();

//        new SpringApplicationBuilder(LLMApplication.class)
//                .sources(ContainersConfig.class)
//                .run(args);

    }

}
