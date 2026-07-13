package com.personalenglishai.backend.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class VocabularyFreshSchemaMySqlTest {
    private static final String DATABASE_PREFIX = "peai_vocab_fresh_";

    @Test
    void freshReadmePathContainsAllReviewSemanticsColumns() throws Exception {
        withFreshSchema((connection, database) -> {
            assertEquals(1, countColumn(connection, database,
                    "vocabulary_card", "conflict_candidate_revision_uid"));
            assertEquals(1, countColumn(connection, database,
                    "vocabulary_generation_job", "generation_outcome"));
            assertEquals(1, countColumn(connection, database,
                    "vocabulary_generation_job", "warning"));
        });
    }

    @Test
    void freshReadmePathSupportsCardMapperSelect() throws Exception {
        withFreshSchema((connection, database) -> {
            seedCard(connection);

            BoundSql select = mappedStatement(
                    "mapper/VocabularyCardMapper.xml",
                    "com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper.findByUidIncludingDeleted")
                    .getBoundSql(Map.of("cardUid", "card_fresh"));
            try (PreparedStatement statement = prepare(connection, select, Map.of("cardUid", "card_fresh"));
                    ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("card_fresh", result.getString("card_uid"));
                assertNull(result.getString("conflict_candidate_revision_uid"));
            }
        });
    }

    @Test
    void freshReadmePathSupportsGenerationJobMapperInsertAndSelect() throws Exception {
        withFreshSchema((connection, database) -> {
            seedCard(connection);
            Map<String, Object> job = generationJobParameters();

            BoundSql insert = mappedStatement(
                    "mapper/VocabularyGenerationJobMapper.xml",
                    "com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper.insertJob")
                    .getBoundSql(job);
            try (PreparedStatement statement = prepare(connection, insert, job)) {
                assertEquals(1, statement.executeUpdate());
            }

            Map<String, Object> selectParameters = Map.of("cardUid", "card_fresh");
            BoundSql select = mappedStatement(
                    "mapper/VocabularyGenerationJobMapper.xml",
                    "com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper.findLatestByCard")
                    .getBoundSql(selectParameters);
            try (PreparedStatement statement = prepare(connection, select, selectParameters);
                    ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("job_fresh", result.getString("job_uid"));
                assertEquals("pending", result.getString("generation_outcome"));
                assertEquals("fresh_warning", result.getString("warning"));
            }
        });
    }

    private void withFreshSchema(FreshSchemaAssertion assertion) throws Exception {
        String url = System.getenv("VOCABULARY_MYSQL_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set VOCABULARY_MYSQL_INTEGRATION_URL to run the MySQL 8 fresh-schema test");
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
                }
                runFreshSchemaPath(connection);
                assertion.verify(connection, database);
            } finally {
                assertTrue(database.startsWith(DATABASE_PREFIX), "refusing to drop an unexpected database");
                try (Statement statement = connection.createStatement()) {
                    statement.execute("USE mysql");
                    statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
                }
            }
        }
    }

    private void runFreshSchemaPath(Connection connection) {
        ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migrate_create_vocabulary_deposition_tables.sql"));
        ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migrate_add_vocabulary_themes_and_markdown_cards.sql"));
    }

    private void seedCard(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO vocabulary_card (
                        card_uid, user_id, language, original_term, normalized_term, display_term,
                        template_key, template_version, theme_uid, theme_version, status,
                        active_revision_uid, last_captured_at, deleted_at
                    ) VALUES (
                        'card_fresh', 7, 'en', 'resilient', 'resilient', 'resilient',
                        'basic', 1, 'theme_system_basic', 1, 'generating', NULL, CURRENT_TIMESTAMP, NULL
                    )
                    """);
        }
    }

    private Map<String, Object> generationJobParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("jobUid", "job_fresh");
        parameters.put("cardUid", "card_fresh");
        parameters.put("baseRevisionUid", null);
        parameters.put("templateKey", "basic");
        parameters.put("templateVersion", 1);
        parameters.put("themeUid", "theme_system_basic");
        parameters.put("themeVersion", 1);
        parameters.put("status", "pending");
        parameters.put("attemptCount", 0);
        parameters.put("requestJson", "{}");
        parameters.put("resultRevisionUid", null);
        parameters.put("errorCode", null);
        parameters.put("errorMessage", null);
        parameters.put("generationOutcome", "pending");
        parameters.put("warning", "fresh_warning");
        parameters.put("availableAt", Timestamp.valueOf(LocalDateTime.of(2026, 7, 13, 8, 0)));
        parameters.put("startedAt", null);
        parameters.put("leaseToken", null);
        parameters.put("leaseExpiresAt", null);
        parameters.put("finishedAt", null);
        return parameters;
    }

    private MappedStatement mappedStatement(String resource, String statementId) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(statementId);
    }

    private PreparedStatement prepare(
            Connection connection,
            BoundSql boundSql,
            Map<String, Object> parameters) throws Exception {
        PreparedStatement statement = connection.prepareStatement(boundSql.getSql());
        int index = 1;
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            statement.setObject(index++, parameters.get(mapping.getProperty()));
        }
        return statement;
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
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    @FunctionalInterface
    private interface FreshSchemaAssertion {
        void verify(Connection connection, String database) throws Exception;
    }
}
