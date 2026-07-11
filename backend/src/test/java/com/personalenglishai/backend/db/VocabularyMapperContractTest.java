package com.personalenglishai.backend.db;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyMapperContractTest {
    @Test
    void mapperXmlContainsOwnershipAndAtomicJobGuards() throws Exception {
        String cards = readMapper("VocabularyCardMapper.xml");
        String sources = readMapper("VocabularySourceMapper.xml");
        String revisions = readMapper("VocabularyRevisionMapper.xml");
        String jobs = readMapper("VocabularyGenerationJobMapper.xml");
        String preferences = readMapper("UserVocabularyPreferenceMapper.xml");

        assertAll(
                () -> assertTrue(cards.contains("user_id = #{userId}")),
                () -> assertTrue(cards.contains("deleted_at IS NULL")),
                () -> assertTrue(cards.contains("active_revision_uid = #{baseRevisionUid}")),
                () -> assertTrue(cards.contains("EXISTS")),
                () -> assertTrue(cards.contains("source_type = #{sourceType}")),
                () -> assertTrue(sources.contains("id=\"insertSource\"")),
                () -> assertTrue(sources.contains("id=\"findSourceByIdempotencyKey\"")),
                () -> assertTrue(sources.contains("id=\"listDistinctSourceTypesByCardUids\"")),
                () -> assertTrue(revisions.contains("id=\"insertRevision\"")),
                () -> assertTrue(revisions.contains("id=\"listRevisions\"")),
                () -> assertTrue(jobs.contains("status = 'pending'")),
                () -> assertTrue(jobs.contains("id=\"cancelActiveForCard\"")),
                () -> assertTrue(jobs.contains("id=\"retryFailed\"")),
                () -> assertTrue(jobs.contains("available_at &lt;= CURRENT_TIMESTAMP")),
                () -> assertTrue(jobs.contains("attempt_count &lt; 3")),
                () -> assertTrue(jobs.contains("lease_token = #{leaseToken}")),
                () -> assertTrue(preferences.contains("ON DUPLICATE KEY UPDATE"))
        );
    }

    @Test
    void atomicClaimUsesDatabaseTimeAndAttemptFence() throws Exception {
        String sql = statementSql("VocabularyGenerationJobMapper", "markRunning", Map.of(
                "jobUid", "job_1",
                "leaseToken", "lease_1",
                "leaseSeconds", 300
        ));

        assertAll(
                () -> assertTrue(sql.contains("lease_token = ?")),
                () -> assertTrue(sql.contains("lease_expires_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP)")),
                () -> assertTrue(sql.contains("attempt_count = attempt_count + 1")),
                () -> assertTrue(sql.contains("status = 'pending'")),
                () -> assertTrue(sql.contains("attempt_count < 3")),
                () -> assertTrue(!sql.contains("lease_expires_at = ?"))
        );
    }

    @Test
    void runningTransitionsRequireCurrentUnexpiredLeaseToken() throws Exception {
        String succeeded = statementSql("VocabularyGenerationJobMapper", "markSucceeded", Map.of(
                "jobUid", "job_1", "leaseToken", "lease_1", "revisionUid", "rev_1"));
        String failed = statementSql("VocabularyGenerationJobMapper", "markFailed", Map.of(
                "jobUid", "job_1",
                "leaseToken", "lease_1",
                "errorCode", "AI_TIMEOUT",
                "errorMessage", "timeout",
                "availableAt", "2026-07-11T12:00:00",
                "terminal", false));
        String cancelled = statementSql("VocabularyGenerationJobMapper", "cancel", Map.of(
                "jobUid", "job_1", "leaseToken", "lease_1"));

        for (String sql : List.of(succeeded, failed, cancelled)) {
            assertAll(
                    () -> assertTrue(sql.contains("status = 'running'")),
                    () -> assertTrue(sql.contains("lease_token = ?")),
                    () -> assertTrue(sql.contains("lease_expires_at > CURRENT_TIMESTAMP")),
                    () -> assertTrue(sql.contains("lease_token = NULL")),
                    () -> assertTrue(sql.contains("lease_expires_at = NULL"))
            );
        }
    }

    @Test
    void latestGenerationStateIsLoadedInOneOwnedBatch() throws Exception {
        String sql = statementSql("VocabularyGenerationJobMapper", "listLatestByCardUids", Map.of(
                "userId", 7L,
                "cardUids", List.of("card_1", "card_2")));

        assertAll(
                () -> assertTrue(sql.contains("MAX(candidate.id)")),
                () -> assertTrue(sql.contains("candidate.card_uid IN")),
                () -> assertTrue(sql.contains("card.user_id = ?")),
                () -> assertTrue(sql.contains("card.deleted_at IS NULL"))
        );
    }

    @Test
    void cardListSearchesOriginalAndActiveDefinitionsAndSupportsBothSorts() throws Exception {
        Map<String, Object> base = Map.of(
                "userId", 7L,
                "keyword", "idea",
                "status", "ready",
                "sourceType", "manual",
                "offset", 0,
                "limit", 20);
        Map<String, Object> az = new java.util.HashMap<>(base);
        az.put("sort", "az");
        Map<String, Object> recent = new java.util.HashMap<>(base);
        recent.put("sort", "recent");
        String azSql = statementSql("VocabularyCardMapper", "listByUser", az);
        String recentSql = statementSql("VocabularyCardMapper", "listByUser", recent);

        assertAll(
                () -> assertTrue(azSql.contains("original_term LIKE")),
                () -> assertTrue(azSql.contains("revision.revision_uid = vocabulary_card.active_revision_uid")),
                () -> assertTrue(azSql.contains("JSON_EXTRACT(revision.content_json, '$.definitions')")),
                () -> assertTrue(azSql.contains("ORDER BY normalized_term ASC")),
                () -> assertTrue(recentSql.contains("ORDER BY last_captured_at DESC")),
                () -> assertTrue(azSql.contains("source_type = ?")),
                () -> assertTrue(azSql.contains("LIMIT ? OFFSET ?"))
        );
    }

    @Test
    void restoreAndTouchIsScopedToOwningUser() throws Exception {
        String sql = statementSql("VocabularyCardMapper", "restoreAndTouch", Map.of(
                "userId", 7L,
                "cardUid", "card_1",
                "displayTerm", "innovative",
                "status", "generating",
                "capturedAt", "2026-07-10T12:00:00"
        ));

        assertAll(
                () -> assertTrue(sql.contains("WHERE user_id = ?")),
                () -> assertTrue(sql.contains("card_uid = ?")),
                () -> assertTrue(sql.contains("deleted_at IS NOT NULL"))
        );
    }

    @Test
    void touchIsScopedToOwningUser() throws Exception {
        String sql = statementSql("VocabularyCardMapper", "touch", Map.of(
                "userId", 7L,
                "cardUid", "card_1",
                "capturedAt", "2026-07-10T12:00:00"
        ));

        assertAll(
                () -> assertTrue(sql.contains("WHERE user_id = ?")),
                () -> assertTrue(sql.contains("card_uid = ?")),
                () -> assertTrue(sql.contains("deleted_at IS NULL"))
        );
    }

    @Test
    void markNeedsReviewIsScopedAndPreservesCardContentPointers() throws Exception {
        String sql = statementSql("VocabularyCardMapper", "markNeedsReview", Map.of(
                "userId", 7L,
                "cardUid", "card_1"
        ));

        assertAll(
                () -> assertTrue(sql.contains("SET status = 'needs_review'")),
                () -> assertTrue(sql.contains("WHERE user_id = ?")),
                () -> assertTrue(sql.contains("card_uid = ?")),
                () -> assertTrue(sql.contains("deleted_at IS NULL")),
                () -> assertTrue(!sql.contains("active_revision_uid")),
                () -> assertTrue(!sql.contains("template_key")),
                () -> assertTrue(!sql.contains("template_version"))
        );
    }

    @Test
    void batchSourceTypesAreDistinctUserScopedAndSoftDeleteSafe() throws Exception {
        String sql = statementSql("VocabularySourceMapper", "listDistinctSourceTypesByCardUids", Map.of(
                "userId", 7L,
                "cardUids", List.of("card_1", "card_2")
        ));

        assertAll(
                () -> assertTrue(sql.contains("GROUP BY source.card_uid, source.user_id, source.source_type")),
                () -> assertTrue(sql.contains("source.user_id = ?")),
                () -> assertTrue(sql.contains("card.user_id = ?")),
                () -> assertTrue(sql.contains("card.deleted_at IS NULL")),
                () -> assertTrue(sql.contains("source.card_uid IN")),
                () -> assertTrue(sql.contains("SUM(COUNT(*)) OVER"))
        );
    }

    @Test
    void staleRunningRecoveryRequeuesOnlyRetryableExpiredLeases() throws Exception {
        String sql = statementSql("VocabularyGenerationJobMapper", "requeueStaleRunning", Map.of());

        assertAll(
                () -> assertTrue(sql.contains("SET status = 'pending'")),
                () -> assertTrue(sql.contains("available_at = CURRENT_TIMESTAMP")),
                () -> assertTrue(sql.contains("started_at = NULL")),
                () -> assertTrue(sql.contains("lease_token = NULL")),
                () -> assertTrue(sql.contains("lease_expires_at = NULL")),
                () -> assertTrue(sql.contains("WHERE status = 'running'")),
                () -> assertTrue(sql.contains("lease_expires_at <= CURRENT_TIMESTAMP")),
                () -> assertTrue(sql.contains("attempt_count < 3"))
        );
    }

    @Test
    void staleRunningRecoveryTerminallyFailsExpiredThirdAttempt() throws Exception {
        String sql = statementSql("VocabularyGenerationJobMapper", "failStaleRunning", Map.of());

        assertAll(
                () -> assertTrue(sql.contains("UPDATE vocabulary_generation_job job")),
                () -> assertTrue(sql.contains("INNER JOIN vocabulary_card card")),
                () -> assertTrue(sql.contains("SET job.status = 'failed'")),
                () -> assertTrue(sql.contains("job.error_code = 'LEASE_EXPIRED'")),
                () -> assertTrue(sql.contains("job.finished_at = CURRENT_TIMESTAMP")),
                () -> assertTrue(sql.contains("job.lease_token = NULL")),
                () -> assertTrue(sql.contains("job.lease_expires_at = NULL")),
                () -> assertTrue(sql.contains("job.lease_expires_at <= CURRENT_TIMESTAMP")),
                () -> assertTrue(sql.contains("job.attempt_count >= 3")),
                () -> assertTrue(sql.contains("card.active_revision_uid IS NULL")),
                () -> assertTrue(sql.contains("card.status = 'generating'")),
                () -> assertTrue(sql.contains("THEN 'failed' ELSE card.status END"))
        );
    }

    @Test
    void deleteCancelsPendingAndRunningJobsAndRetryResetsOnlyFailedJob() throws Exception {
        String cancelled = statementSql("VocabularyGenerationJobMapper", "cancelActiveForCard", Map.of(
                "cardUid", "card_1"));
        String retried = statementSql("VocabularyGenerationJobMapper", "retryFailed", Map.of(
                "jobUid", "job_1"));

        assertAll(
                () -> assertTrue(cancelled.contains("status IN ('pending', 'running')")),
                () -> assertTrue(cancelled.contains("lease_token = NULL")),
                () -> assertTrue(retried.contains("SET status = 'pending'")),
                () -> assertTrue(retried.contains("attempt_count = 0")),
                () -> assertTrue(retried.contains("WHERE job_uid = ?")),
                () -> assertTrue(retried.contains("status = 'failed'"))
        );
    }

    @Test
    void cardFinalizationLocksRowsAndFailureUpdateCannotRevertReadyCard() throws Exception {
        String cards = readMapper("VocabularyCardMapper.xml");
        String failureSql = statementSql("VocabularyCardMapper", "markGenerationFailed", Map.of(
                "cardUid", "card_1", "terminal", true));

        assertAll(
                () -> assertTrue(cards.contains("id=\"findByUidForUpdate\"")),
                () -> assertTrue(cards.contains("FOR UPDATE")),
                () -> assertTrue(failureSql.contains("active_revision_uid IS NULL")),
                () -> assertTrue(failureSql.contains("status = 'generating'"))
        );
    }

    @Test
    void mapperXmlResourcesParseAndRegisterAllStatements() throws Exception {
        Configuration configuration = new Configuration();
        Map<String, String[]> mapperStatements = Map.of(
                "VocabularyCardMapper", new String[]{
                        "findByIdentityIncludingDeleted", "insert", "findByUidIncludingDeleted",
                        "findByUidForUpdate",
                        "restoreAndTouch", "touch", "markNeedsReview", "findOwnedByUid", "listByUser", "countByUser",
                        "updateActiveRevision", "markConflictCandidate", "markGenerationFailed", "softDelete"
                },
                "VocabularySourceMapper", new String[]{
                        "insertSource", "findSourceByIdempotencyKey", "listSources",
                        "listDistinctSourceTypesByCardUids"
                },
                "VocabularyRevisionMapper", new String[]{
                        "insertRevision", "findRevision", "listRevisions"
                },
                "VocabularyGenerationJobMapper", new String[]{
                        "insertJob", "selectClaimable", "findLatestByCard", "listLatestByCardUids", "markRunning",
                        "markSucceeded", "markFailed", "cancel", "cancelPendingForCard",
                        "cancelActiveForCard", "retryFailed", "requeueStaleRunning", "failStaleRunning"
                },
                "UserVocabularyPreferenceMapper", new String[]{
                        "findPreferenceByUser", "upsertDefaultTemplate"
                }
        );

        for (Map.Entry<String, String[]> mapper : mapperStatements.entrySet()) {
            String resource = "mapper/" + mapper.getKey() + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertTrue(input != null, () -> "Missing mapper resource: " + resource);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            String namespace = "com.personalenglishai.backend.mapper.vocabulary." + mapper.getKey();
            for (String statement : mapper.getValue()) {
                assertTrue(configuration.hasStatement(namespace + "." + statement),
                        () -> "Missing mapped statement: " + namespace + "." + statement);
            }
        }
    }

    private String readMapper(String fileName) throws Exception {
        return Files.readString(Path.of("src/main/resources/mapper", fileName));
    }

    private String statementSql(String mapperName, String statement, Map<String, Object> parameters)
            throws Exception {
        Configuration configuration = parseVocabularyMappers();
        String namespace = "com.personalenglishai.backend.mapper.vocabulary." + mapperName;
        BoundSql boundSql = configuration.getMappedStatement(namespace + "." + statement)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private Configuration parseVocabularyMappers() throws Exception {
        Configuration configuration = new Configuration();
        for (String mapperName : new String[]{
                "VocabularyCardMapper",
                "VocabularySourceMapper",
                "VocabularyRevisionMapper",
                "VocabularyGenerationJobMapper",
                "UserVocabularyPreferenceMapper"
        }) {
            String resource = "mapper/" + mapperName + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertTrue(input != null, () -> "Missing mapper resource: " + resource);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
