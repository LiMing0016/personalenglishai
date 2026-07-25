package com.personalenglishai.backend.db;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VocabularyCardSearchMySqlIntegrationTest {
    private static final String DATABASE_PREFIX = "peai_vocab_search_";

    @Test
    void mysql8SearchMatchesEnglishChineseCoreMeaningsLegacyDefinitionsAndTerms() throws Exception {
        String url = System.getenv("VOCABULARY_MYSQL_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set VOCABULARY_MYSQL_INTEGRATION_URL to run the MySQL 8 vocabulary search integration test");
        String username = System.getenv("VOCABULARY_MYSQL_INTEGRATION_USERNAME");
        String password = System.getenv("VOCABULARY_MYSQL_INTEGRATION_PASSWORD");
        String database = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = DriverManager.getConnection(
                url, username == null ? "root" : username, password == null ? "" : password)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                statement.execute("USE `" + database + "`");
                createTables(statement);
                seedCards(statement);
            }

            assertEquals(List.of("card_core"), search(connection, "durable"));
            assertEquals(List.of("card_core"), search(connection, "DURABLE"));
            assertEquals(List.of("card_core"), search(connection, "韧性"));
            assertEquals(List.of("card_legacy"), search(connection, "heritage"));
            assertEquals(List.of("card_term"), search(connection, "terminology"));
        } finally {
            try (Connection cleanup = DriverManager.getConnection(
                    url, username == null ? "root" : username, password == null ? "" : password);
                 Statement statement = cleanup.createStatement()) {
                if (database.startsWith(DATABASE_PREFIX)) {
                    statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
                }
            }
        }
    }

    private List<String> search(Connection connection, String keyword) throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 7L);
        parameters.put("keyword", keyword);
        parameters.put("status", null);
        parameters.put("sourceType", null);
        parameters.put("sort", "az");
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        BoundSql boundSql = listStatement().getBoundSql(parameters);

        try (PreparedStatement statement = connection.prepareStatement(boundSql.getSql())) {
            int index = 1;
            for (ParameterMapping mapping : boundSql.getParameterMappings()) {
                statement.setObject(index++, parameters.get(mapping.getProperty()));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> cardUids = new ArrayList<>();
                while (resultSet.next()) {
                    cardUids.add(resultSet.getString("card_uid"));
                }
                return cardUids;
            }
        }
    }

    private org.apache.ibatis.mapping.MappedStatement listStatement() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/VocabularyCardMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(
                "com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper.listByUser");
    }

    private void createTables(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE vocabulary_card (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    card_uid VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    language VARCHAR(16) NOT NULL,
                    original_term VARCHAR(255) NOT NULL,
                    normalized_term VARCHAR(255) NOT NULL,
                    display_term VARCHAR(255) NOT NULL,
                    template_key VARCHAR(32) NOT NULL,
                    template_version INT NOT NULL,
                    theme_uid VARCHAR(64) NULL,
                    theme_version INT NULL,
                    status VARCHAR(24) NOT NULL,
                    active_revision_uid VARCHAR(64) NULL,
                    conflict_candidate_revision_uid VARCHAR(64) NULL,
                    last_captured_at DATETIME NULL,
                    deleted_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        statement.execute("""
                CREATE TABLE vocabulary_card_revision (
                    revision_uid VARCHAR(64) PRIMARY KEY,
                    card_uid VARCHAR(64) NOT NULL,
                    content_json JSON NULL,
                    core_json JSON NULL
                )
                """);
        statement.execute("""
                CREATE TABLE vocabulary_card_source (
                    card_uid VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    source_type VARCHAR(32) NOT NULL
                )
                """);
    }

    private void seedCards(Statement statement) throws Exception {
        statement.execute("""
                INSERT INTO vocabulary_card
                    (card_uid, user_id, language, original_term, normalized_term, display_term,
                     template_key, template_version, status, active_revision_uid, last_captured_at)
                VALUES
                    ('card_core', 7, 'en', 'resilient', 'resilient', 'resilient', 'basic', 1, 'ready', 'rev_core', NOW()),
                    ('card_legacy', 7, 'en', 'legacy', 'legacy', 'legacy', 'basic', 1, 'ready', 'rev_legacy', NOW()),
                    ('card_term', 7, 'en', 'terminology', 'terminology', 'Terminology', 'basic', 1, 'ready', 'rev_term', NOW()),
                    ('card_other', 7, 'en', 'unrelated', 'unrelated', 'unrelated', 'basic', 1, 'ready', 'rev_other', NOW())
                """);
        statement.execute("""
                INSERT INTO vocabulary_card_revision (revision_uid, card_uid, content_json, core_json)
                VALUES
                    ('rev_core', 'card_core', JSON_OBJECT('term', 'resilient'),
                        JSON_OBJECT('schemaVersion', 1, 'term', 'resilient', 'phonetics', JSON_ARRAY(),
                            'senses', JSON_ARRAY(JSON_OBJECT('partOfSpeech', 'adjective',
                                'meanings', JSON_ARRAY(JSON_OBJECT(
                                    'definitionEn', 'able to remain durable under pressure',
                                    'definitionZh', '有韧性的方案')))))),
                    ('rev_legacy', 'card_legacy', JSON_OBJECT('definitions', JSON_ARRAY('a heritage meaning')), NULL),
                    ('rev_term', 'card_term', JSON_OBJECT('definitions', JSON_ARRAY()), NULL),
                    ('rev_other', 'card_other', JSON_OBJECT('definitions', JSON_ARRAY('something else')), NULL)
                """);
    }
}
