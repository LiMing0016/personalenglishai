package com.personalenglishai.backend.service.vocabulary;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyGenerationSchedulerTest {

    @Mock private VocabularyGenerationWorker worker;
    @Mock private VocabularyGenerationJobMapper jobs;

    @Test
    void terminallyFailsThenRequeuesExpiredLeasesBeforeClaiming() {
        when(jobs.failStaleRunning()).thenReturn(2);
        when(jobs.requeueStaleRunning()).thenReturn(3);
        VocabularyGenerationScheduler scheduler =
                new VocabularyGenerationScheduler(worker, jobs, true, 7);

        scheduler.runBatch();

        InOrder order = inOrder(jobs, worker);
        order.verify(jobs).failStaleRunning();
        order.verify(jobs).requeueStaleRunning();
        order.verify(worker).processPendingJobs(7);
    }

    @Test
    void disabledSchedulerDoesNotRecoverOrClaimJobs() {
        VocabularyGenerationScheduler scheduler =
                new VocabularyGenerationScheduler(worker, jobs, false, 5);

        scheduler.runBatch();

        verifyNoInteractions(worker, jobs);
    }
}
