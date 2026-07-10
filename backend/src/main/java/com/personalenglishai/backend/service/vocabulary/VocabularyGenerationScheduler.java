package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VocabularyGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(VocabularyGenerationScheduler.class);

    private final VocabularyGenerationWorker worker;
    private final VocabularyGenerationJobMapper jobs;
    private final boolean enabled;
    private final int batchSize;
    private final long leaseMs;
    private final Clock clock;

    @Autowired
    public VocabularyGenerationScheduler(
            VocabularyGenerationWorker worker,
            VocabularyGenerationJobMapper jobs,
            @Value("${vocabulary.generation.scheduler.enabled:true}") boolean enabled,
            @Value("${vocabulary.generation.scheduler.batch-size:5}") int batchSize,
            @Value("${vocabulary.generation.scheduler.lease-ms:300000}") long leaseMs) {
        this(worker, jobs, enabled, batchSize, leaseMs, Clock.systemDefaultZone());
    }

    VocabularyGenerationScheduler(
            VocabularyGenerationWorker worker,
            VocabularyGenerationJobMapper jobs,
            boolean enabled,
            int batchSize,
            long leaseMs,
            Clock clock) {
        this.worker = worker;
        this.jobs = jobs;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.leaseMs = Math.max(1L, leaseMs);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${vocabulary.generation.scheduler.fixed-delay-ms:5000}")
    public void runBatch() {
        if (!enabled) {
            return;
        }
        LocalDateTime staleBefore = LocalDateTime.ofInstant(
                clock.instant().minusMillis(leaseMs), clock.getZone());
        int recovered = jobs.requeueStaleRunning(staleBefore);
        if (recovered > 0) {
            log.info("Recovered stale vocabulary generation jobs count={} leaseMs={}", recovered, leaseMs);
        }
        worker.processPendingJobs(batchSize);
    }
}
