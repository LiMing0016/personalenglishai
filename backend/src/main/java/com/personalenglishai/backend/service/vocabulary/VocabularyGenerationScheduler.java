package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.mapper.vocabulary.VocabularyGenerationJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public VocabularyGenerationScheduler(
            VocabularyGenerationWorker worker,
            VocabularyGenerationJobMapper jobs,
            @Value("${vocabulary.generation.scheduler.enabled:true}") boolean enabled,
            @Value("${vocabulary.generation.scheduler.batch-size:5}") int batchSize) {
        this.worker = worker;
        this.jobs = jobs;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${vocabulary.generation.scheduler.fixed-delay-ms:5000}")
    public void runBatch() {
        if (!enabled) {
            return;
        }
        int terminal = jobs.failStaleRunning();
        int recovered = jobs.requeueStaleRunning();
        if (terminal > 0 || recovered > 0) {
            log.info(
                    "Recovered stale vocabulary generation jobs requeued={} terminal={}",
                    recovered, terminal);
        }
        worker.processPendingJobs(batchSize);
    }
}
