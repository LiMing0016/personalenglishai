package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningCaptureSchemaTest {

    @Test
    void schemaAndMigrationContainLearningCaptureTables() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/migrate_create_learning_capture_tables.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS learning_extraction_run")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS learning_raw_candidate")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS learning_evidence")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS learning_extraction_run")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS learning_raw_candidate")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS learning_evidence"))
        );
    }

    @Test
    void learningCaptureTablesContainDedupeAndQueueIndexes() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertAll(
                () -> assertTrue(schema.contains("UNIQUE KEY uk_learning_extraction_run_message_extractor (message_uid, extractor_type)")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_learning_raw_candidate_dedupe (user_id, candidate_type, normalized_text, extractor_type)")),
                () -> assertTrue(schema.contains("UNIQUE KEY uk_learning_evidence_candidate (candidate_uid)")),
                () -> assertTrue(schema.contains("KEY idx_learning_extraction_run_extractor_status (extractor_type, status)")),
                () -> assertTrue(schema.contains("KEY idx_learning_raw_candidate_user_type_seen (user_id, candidate_type, last_seen_at)")),
                () -> assertTrue(schema.contains("KEY idx_learning_evidence_user_status_score (user_id, status, score)"))
        );
    }

    @Test
    void mapperXmlContainsRequiredPersistenceOperations() throws IOException {
        String runMapper = Files.readString(Path.of("src/main/resources/mapper/LearningExtractionRunMapper.xml"));
        String candidateMapper = Files.readString(Path.of("src/main/resources/mapper/LearningRawCandidateMapper.xml"));
        String evidenceMapper = Files.readString(Path.of("src/main/resources/mapper/LearningEvidenceMapper.xml"));

        assertAll(
                () -> assertTrue(runMapper.contains("<update id=\"markProcessing\">")),
                () -> assertTrue(runMapper.contains("<update id=\"updateCompleted\">")),
                () -> assertTrue(runMapper.contains("<update id=\"updateFailed\">")),
                () -> assertTrue(candidateMapper.contains("<insert id=\"insertOrUpdateOccurrence\"")),
                () -> assertTrue(candidateMapper.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(candidateMapper.contains("<update id=\"updateComparisonStatus\">")),
                () -> assertTrue(evidenceMapper.contains("<select id=\"selectPendingByUserAndDateRange\"")),
                () -> assertTrue(evidenceMapper.contains("<update id=\"updateStatus\">"))
        );
    }
}
