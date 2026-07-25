package com.personalenglishai.backend.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class VocabularyLeaseMigrationMySqlTest {
    private static final String JDBC_URL_ENV = "VOCABULARY_MIGRATION_TEST_JDBC_URL";
    private static final String JDBC_USER_ENV = "VOCABULARY_MIGRATION_TEST_JDBC_USER";
    private static final String JDBC_PASSWORD_ENV = "VOCABULARY_MIGRATION_TEST_JDBC_PASSWORD";
    private static final String SCHEMA_PREFIX = "vocab_lease_test_";
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migrate_add_vocabulary_generation_job_leases.sql");

    @Test
    void migratesTableWithoutLeaseColumns() throws Exception {
        verifyScenario("no_columns", Scenario.NO_COLUMNS, false);
    }

    @Test
    void migratesExistingLeaseColumnsWithoutIndex() throws Exception {
        verifyScenario("columns_no_index", Scenario.COLUMNS_WITHOUT_INDEX, false);
    }

    @Test
    void toleratesExistingLeaseColumnsAndIndex() throws Exception {
        verifyScenario("columns_and_index", Scenario.COLUMNS_AND_INDEX, false);
    }

    @Test
    void resumesAfterFirstColumnWasPersistedAndCanRunAgain() throws Exception {
        verifyScenario("interrupted", Scenario.ONLY_LEASE_TOKEN, true);
    }

    private void verifyScenario(String label, Scenario scenario, boolean rerun) throws Exception {
        String jdbcUrl = System.getenv(JDBC_URL_ENV);
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
                () -> JDBC_URL_ENV + " is required for MySQL migration tests");

        String schema = SCHEMA_PREFIX + label + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                System.getenv().getOrDefault(JDBC_USER_ENV, "root"),
                System.getenv().getOrDefault(JDBC_PASSWORD_ENV, ""))) {
            createSchema(connection, schema);
            try {
                useSchema(connection, schema);
                createLegacyTable(connection);
                scenario.prepare(connection);
                insertRunningJob(connection);

                runMigration(connection);
                if (rerun) {
                    runMigration(connection);
                }

                assertLeaseColumns(connection, schema);
                assertSingleRecoveryIndex(connection, schema);
                assertBackfilled(connection);
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    private void createSchema(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");
        }
    }

    private void useSchema(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("USE `" + schema + "`");
        }
    }

    private void createLegacyTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE vocabulary_generation_job (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        status VARCHAR(16) NOT NULL,
                        attempt_count INT NOT NULL DEFAULT 0,
                        available_at DATETIME NOT NULL,
                        started_at DATETIME NULL,
                        created_at DATETIME NOT NULL,
                        PRIMARY KEY (id)
                    ) ENGINE=InnoDB
                    """);
        }
    }

    private void insertRunningJob(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO vocabulary_generation_job
                        (status, attempt_count, available_at, started_at, created_at)
                    VALUES
                        ('running', 1, '2026-07-13 08:00:00', '2026-07-13 08:05:00', '2026-07-13 07:55:00')
                    """);
        }
    }

    private void runMigration(Connection connection) {
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATION));
    }

    private void assertLeaseColumns(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = 'vocabulary_generation_job'
                  AND COLUMN_NAME IN ('lease_token', 'lease_expires_at')
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
            }
        }
    }

    private void assertSingleRecoveryIndex(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(DISTINCT INDEX_NAME),
                       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = 'vocabulary_generation_job'
                  AND INDEX_NAME = 'idx_vocabulary_job_lease'
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
                assertEquals("status,lease_expires_at,attempt_count", result.getString(2));
            }
        }
    }

    private void assertBackfilled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT DATE_FORMAT(lease_expires_at, '%Y-%m-%d %H:%i:%s')
                        FROM vocabulary_generation_job
                        WHERE status = 'running'
                        """)) {
            assertTrue(result.next());
            assertNotNull(result.getString(1));
            assertEquals("2026-07-13 08:05:00", result.getString(1));
        }
    }

    private void dropTestSchema(Connection connection, String schema) throws SQLException {
        assertTrue(schema.startsWith(SCHEMA_PREFIX), "refusing to drop an unexpected schema");
        try (Statement statement = connection.createStatement()) {
            statement.execute("USE mysql");
            statement.execute("DROP DATABASE `" + schema + "`");
        }
    }

    private enum Scenario {
        NO_COLUMNS {
            @Override
            void prepare(Connection connection) {
            }
        },
        COLUMNS_WITHOUT_INDEX {
            @Override
            void prepare(Connection connection) throws SQLException {
                addLeaseColumns(connection);
            }
        },
        COLUMNS_AND_INDEX {
            @Override
            void prepare(Connection connection) throws SQLException {
                addLeaseColumns(connection);
                execute(connection, """
                        ALTER TABLE vocabulary_generation_job
                            ADD KEY idx_vocabulary_job_lease (status, lease_expires_at, attempt_count)
                        """);
            }
        },
        ONLY_LEASE_TOKEN {
            @Override
            void prepare(Connection connection) throws SQLException {
                execute(connection, """
                        ALTER TABLE vocabulary_generation_job
                            ADD COLUMN lease_token VARCHAR(64) NULL AFTER started_at
                        """);
            }
        };

        abstract void prepare(Connection connection) throws SQLException;

        static void addLeaseColumns(Connection connection) throws SQLException {
            execute(connection, """
                    ALTER TABLE vocabulary_generation_job
                        ADD COLUMN lease_token VARCHAR(64) NULL AFTER started_at,
                        ADD COLUMN lease_expires_at DATETIME NULL AFTER lease_token
                    """);
        }

        static void execute(Connection connection, String sql) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }
}
