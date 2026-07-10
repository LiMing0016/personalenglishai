package com.personalenglishai.backend.service.vocabulary;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyGenerationSchedulerTest {

    @Mock private VocabularyGenerationWorker worker;
    @Mock private VocabularyGenerationJobMapper jobs;

    @Test
    void recoversExpiredRunningJobsBeforeClaimingBatch() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T08:00:00Z"), ZoneOffset.UTC);
        VocabularyGenerationScheduler scheduler =
                new VocabularyGenerationScheduler(worker, jobs, true, 7, 300_000L, clock);

        scheduler.runBatch();

        verify(jobs).requeueStaleRunning(LocalDateTime.of(2026, 7, 11, 7, 55));
        verify(worker).processPendingJobs(7);
    }

    @Test
    void disabledSchedulerDoesNotRecoverOrClaimJobs() {
        VocabularyGenerationScheduler scheduler = new VocabularyGenerationScheduler(
                worker, jobs, false, 5, 300_000L, Clock.systemUTC());

        scheduler.runBatch();

        verifyNoInteractions(worker, jobs);
    }
}
