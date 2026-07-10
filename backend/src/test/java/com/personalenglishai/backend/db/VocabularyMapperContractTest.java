package com.personalenglishai.backend.db;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
                () -> assertTrue(revisions.contains("id=\"insertRevision\"")),
                () -> assertTrue(revisions.contains("id=\"listRevisions\"")),
                () -> assertTrue(jobs.contains("status = 'pending'")),
                () -> assertTrue(jobs.contains("available_at &lt;= CURRENT_TIMESTAMP")),
                () -> assertTrue(jobs.contains("WHERE job_uid = #{jobUid} AND status = 'pending'")),
                () -> assertTrue(preferences.contains("ON DUPLICATE KEY UPDATE"))
        );
    }

    @Test
    void mapperXmlResourcesParseAndRegisterAllStatements() throws Exception {
        Configuration configuration = new Configuration();
        Map<String, String[]> mapperStatements = Map.of(
                "VocabularyCardMapper", new String[]{
                        "findByIdentityIncludingDeleted", "insert", "findByUidIncludingDeleted",
                        "restoreAndTouch", "touch", "findOwnedByUid", "listByUser", "countByUser",
                        "updateActiveRevision", "markConflictCandidate", "markGenerationFailed", "softDelete"
                },
                "VocabularySourceMapper", new String[]{
                        "insertSource", "findSourceByIdempotencyKey", "listSources"
                },
                "VocabularyRevisionMapper", new String[]{
                        "insertRevision", "findRevision", "listRevisions"
                },
                "VocabularyGenerationJobMapper", new String[]{
                        "insertJob", "selectClaimable", "findLatestByCard", "markRunning",
                        "markSucceeded", "markFailed", "cancel", "cancelPendingForCard"
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
}
