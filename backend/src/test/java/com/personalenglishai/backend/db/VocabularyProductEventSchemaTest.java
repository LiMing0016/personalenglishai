package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyProductEventSchemaTest {

    @Test
    void freshSchemaAndMigrationContainIdempotentVocabularyProductEvents() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migrate_create_vocabulary_product_events.sql"));

        for (String sql : new String[] {schema, migration}) {
            assertAll(
                    () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_product_event")),
                    () -> assertTrue(sql.contains("event_uid VARCHAR(128) NOT NULL")),
                    () -> assertTrue(sql.contains("user_id BIGINT NOT NULL")),
                    () -> assertTrue(sql.contains("event_name VARCHAR(64) NOT NULL")),
                    () -> assertTrue(sql.contains("trace_id VARCHAR(128) NULL")),
                    () -> assertTrue(sql.contains("session_id VARCHAR(128) NOT NULL")),
                    () -> assertTrue(sql.contains("card_uid VARCHAR(64) NULL")),
                    () -> assertTrue(sql.contains("properties_json JSON NOT NULL")),
                    () -> assertTrue(sql.contains("occurred_at DATETIME(3) NOT NULL")),
                    () -> assertTrue(sql.contains("created_at DATETIME(3) NOT NULL")),
                    () -> assertTrue(sql.contains("updated_at DATETIME(3) NOT NULL")),
                    () -> assertTrue(sql.contains(
                            "UNIQUE KEY uk_vocabulary_product_event_user_uid (user_id, event_uid)")),
                    () -> assertTrue(sql.contains(
                            "KEY idx_vocabulary_product_event_name_time (event_name, occurred_at)")),
                    () -> assertTrue(sql.contains(
                            "KEY idx_vocabulary_product_event_trace_time (trace_id, occurred_at)")),
                    () -> assertTrue(sql.contains(
                            "KEY idx_vocabulary_product_event_card_time (card_uid, occurred_at)"))
            );
        }
    }
}
