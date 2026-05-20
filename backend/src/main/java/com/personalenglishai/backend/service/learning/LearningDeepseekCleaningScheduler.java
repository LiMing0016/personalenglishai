package com.personalenglishai.backend.service.learning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LearningDeepseekCleaningScheduler {
    private static final Logger log = LoggerFactory.getLogger(LearningDeepseekCleaningScheduler.class);

    private final LearningDeepseekCleaningService cleaningService;
    private final boolean enabled;
    private final int batchSize;

    public LearningDeepseekCleaningScheduler(
            LearningDeepseekCleaningService cleaningService,
            @Value("${learning.capture.deepseek.scheduler.enabled:false}") boolean enabled,
            @Value("${learning.capture.deepseek.scheduler.batch-size:10}") int batchSize) {
        this.cleaningService = cleaningService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${learning.capture.deepseek.scheduler.fixed-delay-ms:60000}")
    public void runScheduledBatch() {
        if (!enabled) {
            return;
        }
        int processed = cleaningService.processPendingRuns(batchSize);
        if (processed > 0) {
            log.info("learning deepseek cleaning scheduler processed runs={}", processed);
        }
    }
}
