package com.personalenglishai.backend.docs;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VocabularyDepositionDocsTest {

    @Test
    void docsDescribeMigrationWorkerAndOwnershipBoundary() throws Exception {
        String docs = Files.readString(Path.of("../docs/architecture/vocabulary-deposition.md"));

        assertAll(
                () -> assertTrue(docs.contains("migrate_create_vocabulary_deposition_tables.sql")),
                () -> assertTrue(docs.contains("migrate_add_vocabulary_generation_job_leases.sql")),
                () -> assertTrue(docs.contains("vocabulary.generation.scheduler.enabled")),
                () -> assertTrue(docs.contains("user_dictionary_word_state")),
                () -> assertTrue(docs.contains("vocabulary_card")),
                () -> assertTrue(docs.contains("vocabulary_generation_job")),
                () -> assertTrue(docs.contains("POST /api/vocabulary/captures")),
                () -> assertTrue(docs.contains("baseRevisionUid")),
                () -> assertTrue(docs.contains("409030")),
                () -> assertTrue(docs.contains("/app/vocabulary/cards/:cardUid")),
                () -> assertTrue(docs.contains("keep_current")),
                () -> assertTrue(docs.contains("use_ai")),
                () -> assertTrue(docs.contains("merge_fields")),
                () -> assertFalse(docs.contains("PaddleOCR")));
    }
}
