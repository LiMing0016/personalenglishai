package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyDepositionSchemaTest {
    @Test
    void schemaAndMigrationContainVocabularyAggregate() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql"));
        for (String sql : new String[]{schema, migration}) {
            assertAll(
                    () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_card")),
                    () -> assertTrue(sql.contains("UNIQUE KEY uk_vocabulary_card_identity (user_id, language, normalized_term)")),
                    () -> assertTrue(sql.contains("UNIQUE KEY uk_vocabulary_source_idempotency (user_id, idempotency_key)")),
                    () -> assertTrue(sql.contains("author_type VARCHAR(24) NOT NULL")),
                    () -> assertTrue(sql.contains("UNIQUE KEY uk_user_vocabulary_preference (user_id)")),
                    () -> assertTrue(sql.contains("available_at DATETIME NOT NULL"))
            );
        }
    }
}
