package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCleaningSchemaTest {
    @Test
    void schemaAndMigrationContainDataCleaningTables() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_data_cleaning_tables.sql"));
        String dictionaryMigration = Files.readString(Path.of("src/main/resources/db/migrate_create_dictionary_library_tables.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS data_cleaning_source")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS data_cleaning_job")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS dictionary_library")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS dictionary_entry")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS dictionary_import_job")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS data_cleaning_source")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS data_cleaning_job")),
                () -> assertTrue(dictionaryMigration.contains("CREATE TABLE IF NOT EXISTS dictionary_library")),
                () -> assertTrue(dictionaryMigration.contains("CREATE TABLE IF NOT EXISTS dictionary_entry")),
                () -> assertTrue(dictionaryMigration.contains("CREATE TABLE IF NOT EXISTS dictionary_import_job"))
        );
    }
}
