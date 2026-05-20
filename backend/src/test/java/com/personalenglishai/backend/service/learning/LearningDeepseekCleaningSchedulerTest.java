package com.personalenglishai.backend.service.learning;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LearningDeepseekCleaningSchedulerTest {

    @Test
    void runScheduledBatchProcessesPendingRunsWhenEnabled() {
        LearningDeepseekCleaningService cleaningService = mock(LearningDeepseekCleaningService.class);
        LearningDeepseekCleaningScheduler scheduler = new LearningDeepseekCleaningScheduler(
                cleaningService,
                true,
                7);

        scheduler.runScheduledBatch();

        verify(cleaningService).processPendingRuns(7);
    }

    @Test
    void runScheduledBatchDoesNothingWhenDisabled() {
        LearningDeepseekCleaningService cleaningService = mock(LearningDeepseekCleaningService.class);
        LearningDeepseekCleaningScheduler scheduler = new LearningDeepseekCleaningScheduler(
                cleaningService,
                false,
                7);

        scheduler.runScheduledBatch();

        verify(cleaningService, never()).processPendingRuns(7);
    }
}
