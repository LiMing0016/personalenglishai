package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.ResolveVocabularyConflictRequest;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.mapper.vocabulary.UserVocabularyPreferenceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularySourceMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(VocabularyCardServiceTransactionTest.Config.class)
class VocabularyCardServiceTransactionTest {
    @Autowired VocabularyCardService service;
    @Autowired VocabularyCardMapper cards;
    @Autowired VocabularySourceMapper sources;
    @Autowired VocabularyRevisionMapper revisions;
    @Autowired VocabularyGenerationJobMapper jobs;
    @Autowired CandidateRaceState state;
    @Autowired RecordingTransactionManager transactions;

    @BeforeEach
    void resetState() {
        reset(cards, sources, revisions, jobs);
        state.reset();
        transactions.resetCounts();

        when(cards.findOwnedByUid(7L, "card_1")).thenAnswer(ignored -> state.cardSnapshot());
        when(revisions.findRevision(anyString())).thenAnswer(invocation ->
                state.revision(invocation.getArgument(0)));
        when(revisions.listRevisions("card_1")).thenAnswer(ignored -> state.revisions());
        when(sources.listSources("card_1")).thenReturn(List.of());
        doAnswer(invocation -> {
            state.replaceCandidateAfterResolutionInsert(invocation.getArgument(0));
            return null;
        }).when(revisions).insertRevision(any(VocabularyCardRevision.class));
    }

    @Test
    void replacedCandidateMakesOldResolutionRollbackWithoutClearingNewCandidate() {
        assertThrows(VocabularyRevisionConflictException.class, () -> service.resolveConflict(
                7L,
                "card_1",
                "rev_candidate_old",
                new ResolveVocabularyConflictRequest("keep_current", null)));

        Invocation activation = mockingDetails(cards).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("updateActiveRevision"))
                .findFirst()
                .orElseThrow();
        assertEquals(10, activation.getArguments().length);
        assertEquals("rev_candidate_old", activation.getArgument(9));
        assertEquals("rev_current", state.activeRevisionUid);
        assertEquals("rev_candidate_new", state.candidateRevisionUid);
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean CandidateRaceState state() { return new CandidateRaceState(); }
        @Bean VocabularyCardMapper cards(CandidateRaceState state) {
            return mock(VocabularyCardMapper.class, state::answerCardMapperCall);
        }
        @Bean VocabularySourceMapper sources() { return mock(VocabularySourceMapper.class); }
        @Bean VocabularyRevisionMapper revisions() { return mock(VocabularyRevisionMapper.class); }
        @Bean VocabularyGenerationJobMapper jobs() { return mock(VocabularyGenerationJobMapper.class); }
        @Bean UserVocabularyPreferenceMapper preferences() { return mock(UserVocabularyPreferenceMapper.class); }
        @Bean VocabularyThemeService themeService() { return mock(VocabularyThemeService.class); }
        @Bean VocabularyThemeMapper themes() { return mock(VocabularyThemeMapper.class); }
        @Bean VocabularyRevisionWriteService revisionWriter() { return mock(VocabularyRevisionWriteService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean VocabularyTemplateRegistry templateRegistry(ObjectMapper objectMapper) {
            return new VocabularyTemplateRegistry(objectMapper);
        }
        @Bean VocabularyCoreContentCodec coreCodec(ObjectMapper objectMapper) {
            return new VocabularyCoreContentCodec(objectMapper);
        }
        @Bean RecordingTransactionManager transactionManager() { return new RecordingTransactionManager(); }
        @Bean VocabularyCardService service(
                VocabularyCardMapper cards,
                VocabularySourceMapper sources,
                VocabularyRevisionMapper revisions,
                VocabularyGenerationJobMapper jobs,
                UserVocabularyPreferenceMapper preferences,
                VocabularyThemeService themeService,
                VocabularyThemeMapper themes,
                VocabularyTemplateRegistry templateRegistry,
                VocabularyCoreContentCodec coreCodec,
                ObjectMapper objectMapper,
                VocabularyRevisionWriteService revisionWriter) {
            return new VocabularyCardService(
                    cards, sources, revisions, jobs, preferences, themeService, themes,
                    templateRegistry, coreCodec, objectMapper, revisionWriter);
        }
    }

    static final class CandidateRaceState {
        private final Map<String, VocabularyCardRevision> revisionByUid = new LinkedHashMap<>();
        private String activeRevisionUid;
        private String candidateRevisionUid;
        private String status;

        void reset() {
            revisionByUid.clear();
            activeRevisionUid = "rev_current";
            candidateRevisionUid = "rev_candidate_old";
            status = "needs_review";
            VocabularyCardRevision current = VocabularyTestFixtures.userRevision("rev_current");
            current.setContentJson(validContent("current"));
            revisionByUid.put(current.getRevisionUid(), current);
            VocabularyCardRevision oldCandidate = VocabularyTestFixtures.userRevision("rev_candidate_old");
            oldCandidate.setAuthorType("ai");
            oldCandidate.setContentJson(validContent("old candidate"));
            revisionByUid.put(oldCandidate.getRevisionUid(), oldCandidate);
            VocabularyCardRevision newCandidate = VocabularyTestFixtures.userRevision("rev_candidate_new");
            newCandidate.setAuthorType("ai");
            newCandidate.setContentJson(validContent("new candidate"));
            revisionByUid.put(newCandidate.getRevisionUid(), newCandidate);
        }

        Object answerCardMapperCall(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
            if (!invocation.getMethod().getName().equals("updateActiveRevision")) {
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            Object[] arguments = invocation.getArguments();
            boolean activeMatches = Objects.equals(activeRevisionUid, arguments[2]);
            boolean candidateMatches = arguments.length == 9
                    || Objects.equals(candidateRevisionUid, arguments[9]);
            if (!activeMatches || !candidateMatches) {
                return 0;
            }
            activeRevisionUid = (String) arguments[3];
            candidateRevisionUid = null;
            status = (String) arguments[4];
            return 1;
        }

        VocabularyCard cardSnapshot() {
            VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", activeRevisionUid);
            card.setStatus(status);
            card.setConflictCandidateRevisionUid(candidateRevisionUid);
            return card;
        }

        VocabularyCardRevision revision(String revisionUid) {
            return revisionByUid.get(revisionUid);
        }

        List<VocabularyCardRevision> revisions() {
            return List.copyOf(revisionByUid.values());
        }

        void replaceCandidateAfterResolutionInsert(VocabularyCardRevision resolution) {
            revisionByUid.put(resolution.getRevisionUid(), resolution);
            candidateRevisionUid = "rev_candidate_new";
        }

        private String validContent(String notes) {
            return """
                    {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                    "definitions":[],"examples":[],"notes":"%s"}
                    """.formatted(notes);
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        void resetCounts() {
            commits = 0;
            rollbacks = 0;
        }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) throws TransactionException { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) throws TransactionException { rollbacks++; }
    }
}
