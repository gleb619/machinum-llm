package machinum;

import lombok.extern.slf4j.Slf4j;
import machinum.service.plugin.StatisticPlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class LLMApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(LLMApplication.class, args);
        context.getBean(StatisticPlugin.class).init();
    }

}
