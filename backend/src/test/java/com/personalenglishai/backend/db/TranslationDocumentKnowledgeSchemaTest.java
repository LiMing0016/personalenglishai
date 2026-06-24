package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationDocumentKnowledgeSchemaTest {

    @Test
    void schemaAndMigrationContainTranslationKnowledgeTables() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_translation_document_knowledge_tables.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS translation_document_parse_snapshot")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS translation_document_file")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS translation_document_element")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS translation_knowledge_chunk")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS translation_document_asset")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS translation_document_parse_snapshot")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS translation_document_file")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS translation_document_element")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS translation_knowledge_chunk")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS translation_document_asset"))
        );
    }

    @Test
    void translationKnowledgeTablesContainReuseAndIterationIndexes() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertAll(
                () -> assertTrue(schema.contains("UNIQUE KEY uk_translation_parse_snapshot_document (document_id)")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_translation_document_file_document (document_id)")),
                () -> assertTrue(schema.contains("KEY idx_translation_document_file_sha256 (sha256)")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_translation_element_document_element (document_id, element_id)")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_translation_chunk_document_chunk (document_id, chunk_id)")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_translation_asset_document_asset (document_id, asset_id)")),
                () -> assertTrue(schema.contains("KEY idx_translation_chunk_document_page (document_id, first_page_number, chunk_order)")),
                () -> assertTrue(schema.contains("KEY idx_translation_element_document_page (document_id, page_number, element_order)")),
                () -> assertTrue(schema.contains("KEY idx_translation_asset_document_page (document_id, page_number)"))
        );
    }

    @Test
    void mapperXmlContainsSnapshotAndChunkOperations() throws IOException {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/TranslationDocumentKnowledgeMapper.xml"));

        assertAll(
                () -> assertTrue(mapper.contains("<insert id=\"insertSnapshot\"")),
                () -> assertTrue(mapper.contains("<insert id=\"insertElement\"")),
                () -> assertTrue(mapper.contains("<insert id=\"insertChunk\"")),
                () -> assertTrue(mapper.contains("<insert id=\"insertAsset\"")),
                () -> assertTrue(mapper.contains("<select id=\"findSnapshotByDocumentId\"")),
                () -> assertTrue(mapper.contains("<select id=\"selectChunksByDocumentId\"")),
                () -> assertTrue(mapper.contains("<delete id=\"deleteByDocumentId\""))
        );
    }
}
