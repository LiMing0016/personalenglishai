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
                    () -> assertTrue(sql.contains("available_at DATETIME NOT NULL")),
                    () -> assertTrue(sql.contains("lease_token VARCHAR(64) NULL")),
                    () -> assertTrue(sql.contains("lease_expires_at DATETIME NULL")),
                    () -> assertTrue(sql.contains(
                            "KEY idx_vocabulary_job_lease (status, lease_expires_at, attempt_count)"))
            );
        }
    }

    @Test
    void vocabularyIdentityUsesAccentSensitiveMysqlCollationAndHasAnUpgradeMigration() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String initialMigration = Files.readString(Path.of(
                "src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql"));
        String upgradeMigration = Files.readString(Path.of(
                "src/main/resources/db/migrate_make_vocabulary_identity_exact.sql"));

        for (String sql : new String[]{schema, initialMigration}) {
            assertTrue(sql.contains(
                    "normalized_term VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL"));
        }
        assertAll(
                () -> assertTrue(upgradeMigration.contains("ALTER TABLE vocabulary_card")),
                () -> assertTrue(upgradeMigration.contains(
                        "MODIFY normalized_term VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL"))
        );
    }

    @Test
    void leaseUpgradeMigrationAddsFieldsAndRecoveryIndexForExistingVocabularyJobs() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migrate_add_vocabulary_generation_job_leases.sql"));

        assertAll(
                () -> assertTrue(migration.contains("ALTER TABLE vocabulary_generation_job")),
                () -> assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS lease_token VARCHAR(64) NULL")),
                () -> assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS lease_expires_at DATETIME NULL")),
                () -> assertTrue(migration.contains("ADD KEY idx_vocabulary_job_lease")),
                () -> assertTrue(migration.contains("SET lease_expires_at = COALESCE(started_at, available_at, created_at, CURRENT_TIMESTAMP)")),
                () -> assertTrue(migration.contains("WHERE status = 'running'")),
                () -> assertTrue(migration.contains("AND lease_expires_at IS NULL"))
        );
    }
}
