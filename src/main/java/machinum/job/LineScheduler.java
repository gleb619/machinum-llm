package machinum.job;

import machinum.repository.LineDao;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.async.AsyncHelper;
import org.springframework.db.DbHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This component is responsible for scheduling the refresh of a materialized view named 'lines_info'.
 * It uses Spring's scheduling capabilities to perform this task periodically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineScheduler {

    private final AsyncHelper asyncHelper;
    private final DbHelper dbHelper;
    private final LineDao lineDao;

    /**
     * Refreshes the materialized view on application startup asynchronously.
     */
    @PostConstruct
    public void refreshOnStartup() {
        log.info("Refreshing materialized view on application startup...");
        asyncHelper.runAsync(() -> {
            try {
                refreshMaterializedView();
                log.info("Materialized view refreshed successfully on application startup.");
            } catch (Exception e) {
                log.error("Failed to refresh materialized view on application startup.", e);
            }
        });
    }

    /**
     * Refreshes the materialized view every hour.
     */
    @Scheduled(cron = "0 0 * * * ?") // Runs at the start of every hour
    public void refreshHourly() {
        log.info("Refreshing materialized view hourly...");
        try {
            refreshMaterializedView();
            log.info("Materialized view refreshed successfully hourly.");
        } catch (Exception e) {
            log.error("Failed to refresh materialized view hourly.", e);
        }
    }

    /**
     * Executes the SQL command to refresh the materialized view within a new transaction.
     */
    private void refreshMaterializedView() {
        dbHelper.doInNewTransaction(lineDao::refreshMaterializedView);
        dbHelper.noTransaction(lineDao::vacuumMaterializedView);
    }
}