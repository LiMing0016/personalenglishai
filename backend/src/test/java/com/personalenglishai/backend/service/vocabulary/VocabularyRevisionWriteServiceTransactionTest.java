package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(VocabularyRevisionWriteServiceTransactionTest.Config.class)
class VocabularyRevisionWriteServiceTransactionTest {
    @Autowired VocabularyRevisionWriteService service;
    @Autowired VocabularyCardMapper cards;
    @Autowired VocabularyRevisionMapper revisions;
    @Autowired RecordingTransactionManager transactions;

    @BeforeEach
    void resetState() {
        reset(cards, revisions);
        transactions.resetCounts();
    }

    @Test
    void staleActivationCommitsAppendedCandidateBeforeReturningOutcome() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_old");
        VocabularyCardRevision candidate = VocabularyTestFixtures.userRevision("rev_candidate");
        candidate.setBaseRevisionUid("rev_old");
        candidate.setThemeUid("theme_user_1");
        candidate.setThemeVersion(3);
        when(cards.updateActiveRevision(eq(7L), eq("card_1"), eq("rev_old"), eq("rev_candidate"),
                eq("ready"), eq("basic"), eq(1), eq("theme_user_1"), eq(3))).thenReturn(0);
        when(cards.markConflictCandidate("card_1", "rev_candidate")).thenReturn(1);

        var outcome = service.appendAndActivate(7L, card, candidate);

        assertEquals(VocabularyRevisionWriteService.WriteOutcome.STALE, outcome);
        verify(revisions).insertRevision(candidate);
        verify(cards).markConflictCandidate("card_1", "rev_candidate");
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    void successfulActivationSynchronizesThemeFromActivatedRevision() {
        VocabularyCard card = VocabularyTestFixtures.ready("card_1", 7L, "innovative", "rev_old");
        card.setThemeUid("theme_old");
        card.setThemeVersion(1);
        VocabularyCardRevision revision = VocabularyTestFixtures.userRevision("rev_user");
        revision.setBaseRevisionUid("rev_old");
        revision.setThemeUid("theme_frozen");
        revision.setThemeVersion(4);
        when(cards.updateActiveRevision(7L, "card_1", "rev_old", "rev_user",
                "ready", "basic", 1, "theme_frozen", 4)).thenReturn(1);

        assertEquals(VocabularyRevisionWriteService.WriteOutcome.ACTIVATED,
                service.appendAndActivate(7L, card, revision));

        verify(cards).updateActiveRevision(7L, "card_1", "rev_old", "rev_user",
                "ready", "basic", 1, "theme_frozen", 4);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean VocabularyCardMapper cards() { return mock(VocabularyCardMapper.class); }
        @Bean VocabularyRevisionMapper revisions() { return mock(VocabularyRevisionMapper.class); }
        @Bean RecordingTransactionManager transactionManager() { return new RecordingTransactionManager(); }
        @Bean VocabularyRevisionWriteService service(VocabularyCardMapper cards, VocabularyRevisionMapper revisions) {
            return new VocabularyRevisionWriteService(cards, revisions);
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
