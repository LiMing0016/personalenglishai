package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingPromptSheetSchemaTest {

    @Test
    void schemaAndMigrationContainWritingPromptSheetDefinition() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_writing_prompt_sheet.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS writing_prompt_sheet")),
                () -> assertTrue(schema.contains("paper VARCHAR(64) NOT NULL")),
                () -> assertTrue(schema.contains("topic_title VARCHAR(255) NOT NULL")),
                () -> assertTrue(schema.contains("attachment_type VARCHAR(16) NOT NULL DEFAULT 'none'")),
                () -> assertTrue(schema.contains("attachment_payload_json JSON NULL")),
                () -> assertTrue(schema.contains("structured_payload_json JSON NULL")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS writing_prompt_sheet")),
                () -> assertTrue(migration.contains("topic_title VARCHAR(255) NOT NULL")),
                () -> assertTrue(migration.contains("attachment_payload_json JSON NULL")),
                () -> assertTrue(migration.contains("structured_payload_json JSON NULL"))
        );
    }

    @Test
    void schemaAndMigrationContainHandwritingImportFields() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_add_handwriting_import_fields.sql"));

        assertAll(
                () -> assertTrue(schema.contains("handwritten_source_type VARCHAR(16) NULL")),
                () -> assertTrue(schema.contains("handwritten_source_image_url LONGTEXT NULL")),
                () -> assertTrue(schema.contains("handwritten_recognized_text LONGTEXT NULL")),
                () -> assertTrue(schema.contains("handwritten_imported_at DATETIME NULL")),
                () -> assertTrue(migration.contains("ADD COLUMN handwritten_source_type VARCHAR(16) NULL")),
                () -> assertTrue(migration.contains("ADD COLUMN handwritten_source_image_url LONGTEXT NULL")),
                () -> assertTrue(migration.contains("ADD COLUMN handwritten_recognized_text LONGTEXT NULL")),
                () -> assertTrue(migration.contains("ADD COLUMN handwritten_imported_at DATETIME NULL"))
        );
    }
}
