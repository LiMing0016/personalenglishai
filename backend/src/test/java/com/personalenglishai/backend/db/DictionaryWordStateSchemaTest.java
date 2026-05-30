package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryWordStateSchemaTest {
    @Test
    void schemaAndMigrationContainUserDictionaryWordState() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_user_dictionary_word_state.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS user_dictionary_word_state")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_user_dictionary_word (user_id, normalized_word)")),
                () -> assertTrue(schema.contains("lookup_count INT NOT NULL DEFAULT 0")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS user_dictionary_word_state")),
                () -> assertTrue(migration.contains("UNIQUE KEY uk_user_dictionary_word (user_id, normalized_word)")),
                () -> assertTrue(migration.contains("lookup_count INT NOT NULL DEFAULT 0"))
        );
    }
}
