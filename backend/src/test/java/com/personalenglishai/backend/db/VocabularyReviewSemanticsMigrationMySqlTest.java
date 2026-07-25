package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VocabularyReviewSemanticsMigrationMySqlTest {
    private static final String DATABASE_PREFIX = "peai_vocab_semantics_";

    @Test
    void migrationIsAdditiveAndRerunnableOnMySql8() throws Exception {
        String url = System.getenv("VOCABULARY_MYSQL_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set VOCABULARY_MYSQL_INTEGRATION_URL to run the MySQL 8 vocabulary migration test");
        String username = System.getenv("VOCABULARY_MYSQL_INTEGRATION_USERNAME");
        String password = System.getenv("VOCABULARY_MYSQL_INTEGRATION_PASSWORD");
        String database = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = DriverManager.getConnection(
                url, username == null ? "root" : username, password == null ? "" : password)) {
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + database
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    statement.execute("USE `" + database + "`");
                    statement.execute("CREATE TABLE vocabulary_card (id BIGINT PRIMARY KEY)");
                    statement.execute("CREATE TABLE vocabulary_generation_job (id BIGINT PRIMARY KEY)");
                }

                runMigration(connection);
                runMigration(connection);

                assertEquals(1, countColumn(connection, database,
                        "vocabulary_card", "conflict_candidate_revision_uid"));
                assertEquals(1, countColumn(connection, database,
                        "vocabulary_generation_job", "generation_outcome"));
                assertEquals(1, countColumn(connection, database,
                        "vocabulary_generation_job", "warning"));
            } finally {
                assertTrue(database.startsWith(DATABASE_PREFIX), "refusing to drop an unexpected database");
                try (Statement statement = connection.createStatement()) {
                    statement.execute("USE mysql");
                    statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
                }
            }
        }
    }

    private void runMigration(Connection connection) {
        ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migrate_add_vocabulary_review_semantics.sql"));
    }

    private int countColumn(Connection connection, String database, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, database);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }
}
