package com.personalenglishai.backend.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VocabularyGenerationMetadataMigrationMySqlTest {
    private static final String DATABASE_PREFIX = "peai_vocab_generation_metadata_";

    @Test
    void keepsMapperAssertionFailurePrimaryAndSuppressesSchemaCleanupFailure() {
        String database = randomDisposableSchemaName();
        AssertionError mapperAssertionFailure = new AssertionError("mapper assertion failed");
        SQLException cleanupFailure = new SQLException("drop failed");

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> executeWithDisposableSchemaCleanup(database,
                        () -> {
                            throw mapperAssertionFailure;
                        },
                        () -> {
                            throw cleanupFailure;
                        }));

        assertSame(mapperAssertionFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0].getMessage().contains(database));
        assertSame(cleanupFailure, thrown.getSuppressed()[0].getCause());
    }

    @Test
    void failsWithSchemaQualifiedCleanupFailureAfterSuccessfulMigration() {
        String database = randomDisposableSchemaName();
        SQLException cleanupFailure = new SQLException("drop failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> executeWithDisposableSchemaCleanup(database,
                        () -> {
                        },
                        () -> {
                            throw cleanupFailure;
                        }));

        assertTrue(thrown.getMessage().contains(database));
        assertSame(cleanupFailure, thrown.getCause());
    }

    @Test
    void migrationIsAdditiveAndRerunnableOnMySql8() throws Throwable {
        String url = System.getenv("VOCABULARY_MYSQL_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set VOCABULARY_MYSQL_INTEGRATION_URL to run the MySQL 8 vocabulary migration test");
        String username = System.getenv("VOCABULARY_MYSQL_INTEGRATION_USERNAME");
        String password = System.getenv("VOCABULARY_MYSQL_INTEGRATION_PASSWORD");
        String database = randomDisposableSchemaName();

        try (Connection connection = DriverManager.getConnection(
                url, username == null ? "root" : username, password == null ? "" : password)) {
            executeWithDisposableSchemaCleanup(database, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + database
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    statement.execute("USE `" + database + "`");
                    createLegacyRevisionTable(statement);
                }

                runMigration(connection);
                runMigration(connection);

                assertGenerationMetadataColumn(connection, database);
                assertMapperRoundTripsGenerationMetadata(connection);
            }, () -> dropDisposableDatabase(connection, database));
        }
    }

    private static void executeWithDisposableSchemaCleanup(
            String database, ThrowingAction action, ThrowingAction cleanup) throws Throwable {
        Throwable primaryFailure = null;
        try {
            action.run();
        } catch (Throwable error) {
            primaryFailure = error;
            throw error;
        } finally {
            try {
                cleanup.run();
            } catch (Throwable cleanupError) {
                IllegalStateException schemaCleanupFailure = new IllegalStateException(
                        "Failed to drop disposable MySQL schema '" + database + "'", cleanupError);
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(schemaCleanupFailure);
                } else {
                    throw schemaCleanupFailure;
                }
            }
        }
    }

    private void dropDisposableDatabase(Connection connection, String database) throws SQLException {
        if (!database.startsWith(DATABASE_PREFIX)) {
            throw new IllegalArgumentException("Refusing to drop unexpected MySQL schema '" + database + "'");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }

    private static String randomDisposableSchemaName() {
        return DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private void runMigration(Connection connection) {
        ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migrate_add_vocabulary_generation_metadata.sql"));
    }

    private void createLegacyRevisionTable(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE vocabulary_card_revision (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    revision_uid VARCHAR(64) NOT NULL,
                    card_uid VARCHAR(64) NOT NULL,
                    base_revision_uid VARCHAR(64) NULL,
                    author_type VARCHAR(24) NOT NULL,
                    template_key VARCHAR(32) NOT NULL,
                    template_version INT NOT NULL,
                    theme_uid VARCHAR(64) NULL,
                    theme_version INT NULL,
                    content_json JSON NULL,
                    core_json JSON NULL,
                    content_markdown MEDIUMTEXT NULL,
                    content_format_version INT NULL,
                    change_summary VARCHAR(512) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_vocabulary_revision_uid (revision_uid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void assertGenerationMetadataColumn(Connection connection, String database) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DATA_TYPE, IS_NULLABLE
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, database);
            statement.setString(2, "vocabulary_card_revision");
            statement.setString(3, "generation_metadata_json");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("json", resultSet.getString("DATA_TYPE"));
                assertEquals("YES", resultSet.getString("IS_NULLABLE"));
            }
        }
    }

    private void assertMapperRoundTripsGenerationMetadata(Connection connection) throws Exception {
        try (SqlSession session = revisionMapperSessionFactory(connection).openSession(true)) {
            VocabularyRevisionMapper mapper = session.getMapper(VocabularyRevisionMapper.class);
            String metadata = "{\"provider\":\"openai\",\"model\":\"test-model\"}";

            VocabularyCardRevision withMetadata = revision("revision_metadata", metadata);
            assertEquals(1, mapper.insertRevision(withMetadata));
            JsonNode persistedMetadata = new ObjectMapper().readTree(
                    mapper.findRevision(withMetadata.getRevisionUid()).getGenerationMetadataJson());
            assertEquals("openai", persistedMetadata.path("provider").asText());
            assertEquals("test-model", persistedMetadata.path("model").asText());

            VocabularyCardRevision withoutMetadata = revision("revision_null", null);
            assertEquals(1, mapper.insertRevision(withoutMetadata));
            assertNull(mapper.findRevision(withoutMetadata.getRevisionUid()).getGenerationMetadataJson());
        }
    }

    private SqlSessionFactory revisionMapperSessionFactory(Connection connection) throws Exception {
        Configuration configuration = new Configuration(new Environment(
                "mysql-metadata-integration",
                new JdbcTransactionFactory(),
                new SingleConnectionDataSource(connection, true)));
        String resource = "mapper/VocabularyRevisionMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private VocabularyCardRevision revision(String revisionUid, String generationMetadataJson) {
        VocabularyCardRevision revision = new VocabularyCardRevision();
        revision.setRevisionUid(revisionUid);
        revision.setCardUid("card_metadata");
        revision.setAuthorType("ai");
        revision.setTemplateKey("basic");
        revision.setTemplateVersion(1);
        revision.setContentJson("{\"term\":\"metadata\"}");
        revision.setCoreJson("{\"schemaVersion\":1,\"term\":\"metadata\"}");
        revision.setContentMarkdown("metadata test");
        revision.setContentFormatVersion(1);
        revision.setGenerationMetadataJson(generationMetadataJson);
        revision.setChangeSummary("metadata migration test");
        return revision;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Throwable;
    }
}
