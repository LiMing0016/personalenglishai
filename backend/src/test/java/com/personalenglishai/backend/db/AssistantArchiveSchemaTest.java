package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantArchiveSchemaTest {
    @Test
    void schemaContainsAssistantArchiveTables() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_assistant_archive_tables.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS assistant_conversation_archive")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS assistant_archive_setting")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS assistant_conversation_archive")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS assistant_archive_setting")),
                () -> assertTrue(schema.contains("markdown_path VARCHAR(1000) NOT NULL")),
                () -> assertTrue(schema.contains("archive_dir VARCHAR(1000) NOT NULL"))
        );
    }
}
