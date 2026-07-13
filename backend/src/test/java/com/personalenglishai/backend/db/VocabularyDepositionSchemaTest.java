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
        String normalized = migration.toLowerCase();

        assertAll(
                () -> assertTrue(migration.contains("ALTER TABLE vocabulary_generation_job")),
                () -> assertTrue(migration.contains("FROM information_schema.COLUMNS")),
                () -> assertTrue(migration.contains("FROM information_schema.STATISTICS")),
                () -> assertTrue(migration.contains("TABLE_SCHEMA = DATABASE()")),
                () -> assertTrue(migration.contains("PREPARE vocabulary_lease_migration_stmt")),
                () -> assertTrue(normalized.contains("add column lease_token varchar(64) null")),
                () -> assertTrue(normalized.contains("add column lease_expires_at datetime null")),
                () -> assertTrue(migration.contains("ADD KEY idx_vocabulary_job_lease")),
                () -> assertTrue(migration.contains("SET lease_expires_at = COALESCE(started_at, available_at, created_at, CURRENT_TIMESTAMP)")),
                () -> assertTrue(migration.contains("WHERE status = 'running'")),
                () -> assertTrue(migration.contains("AND lease_expires_at IS NULL"))
        );
    }

    @Test
    void themeAndMarkdownCardMigrationAddsAdditiveSchema() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql"));

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_theme")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vocabulary_theme_revision")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS user_vocabulary_theme_recent")),
                () -> assertTrue(sql.contains("ADD COLUMN theme_uid VARCHAR(64) NULL")),
                () -> assertTrue(sql.contains("ADD COLUMN core_json JSON NULL")),
                () -> assertTrue(sql.contains("ADD COLUMN content_markdown MEDIUMTEXT NULL"))
        );
    }

    @Test
    void themeMigrationSeedsPhysicalSystemThemesAndEnforcesActiveUserNameUniqueness() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migrate_add_vocabulary_themes_and_markdown_cards.sql"));

        assertAll(
                () -> assertTrue(sql.contains("theme_system_basic")),
                () -> assertTrue(sql.contains("theme_system_exam")),
                () -> assertTrue(sql.contains("theme_system_reading")),
                () -> assertTrue(sql.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(sql.contains("uk_vocabulary_theme_active_user_name")),
                () -> assertTrue(sql.contains("active_user_id")),
                () -> assertTrue(sql.contains("active_name"))
        );
    }

    @Test
    void freshSchemaAndReviewSemanticsMigrationContainExplicitReviewColumns() throws Exception {
        String initial = Files.readString(Path.of(
                "src/main/resources/db/migrate_create_vocabulary_deposition_tables.sql"));
        String upgrade = Files.readString(Path.of(
                "src/main/resources/db/migrate_add_vocabulary_review_semantics.sql"));

        assertAll(
                () -> assertTrue(initial.contains("conflict_candidate_revision_uid VARCHAR(64) NULL")),
                () -> assertTrue(initial.contains("generation_outcome VARCHAR(24) NULL")),
                () -> assertTrue(initial.contains("warning VARCHAR(64) NULL")),
                () -> assertTrue(upgrade.contains("conflict_candidate_revision_uid VARCHAR(64) NULL")),
                () -> assertTrue(upgrade.contains("generation_outcome VARCHAR(24) NULL")),
                () -> assertTrue(upgrade.contains("warning VARCHAR(64) NULL")),
                () -> assertTrue(upgrade.contains("information_schema.columns")),
                () -> assertTrue(upgrade.contains("table_schema = DATABASE()"))
        );
    }
}
