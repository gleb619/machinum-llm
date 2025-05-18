package machinum.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigration implements CommandLineRunner {

    @Value("classpath:${test.init-script-path}")
    private final Resource dataFile;
    private final JdbcTemplate jdbcTemplate;


    @Override
    public void run(String... args) {
        try {
            log.info("Starting database migration...");
            String sqlScript = readFileContent(dataFile);
            executeSqlScript(sqlScript);
            log.info("Database migration completed successfully.");
        } catch (Exception e) {
            log.error("Error during database migration: %s".formatted(e.getMessage()), e);
            ExceptionUtils.rethrow(e);
        }
    }

    /**
     * Reads the content of the SQL file as a single string.
     *
     * @param resource The SQL file resource.
     * @return The content of the SQL file.
     * @throws IOException If an error occurs while reading the file.
     */
    private String readFileContent(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Executes the SQL script using JdbcTemplate.
     *
     * @param sqlScript The SQL script to execute.
     */
    private void executeSqlScript(String sqlScript) {
        // Split the script into individual statements (assuming semicolon-separated)
        String[] statements;
        if (sqlScript.contains("/*SEPARATOR*/")) {
            statements = sqlScript.split("/\\*SEPARATOR\\*/");
        } else if (sqlScript.contains("--SEPARATOR--")) {
            statements = sqlScript.split("--SEPARATOR--");
        } else {
            statements = sqlScript.split(";");
        }

        for (String statement : statements) {
            String trimmedStatement = statement.trim();
            if (!trimmedStatement.isEmpty()) {
                log.debug("Executing SQL statement: {}...", toShortDescription(trimmedStatement));
                jdbcTemplate.execute(trimmedStatement);
            }
        }
    }
}
