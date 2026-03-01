package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WritingEvaluateTaskServiceImpl implements WritingEvaluateTaskService {

    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_SUCCEEDED = "succeeded";
    private static final String STATUS_FAILED = "failed";
    private static final long RETAIN_MS = Duration.ofHours(6).toMillis();
    private static final AtomicInteger WORKER_SEQ = new AtomicInteger(0);

    private final WritingEvaluateService writingEvaluateService;
    private final ConcurrentMap<String, EvaluateTaskState> taskStore = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "writing-evaluate-worker-" + WORKER_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public WritingEvaluateTaskServiceImpl(WritingEvaluateService writingEvaluateService) {
        this.writingEvaluateService = writingEvaluateService;
    }

    @Override
    public WritingEvaluateTaskResponse submit(WritingEvaluateRequest request) {
        cleanupExpired();
        String requestId = "eval-task-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        EvaluateTaskState state = new EvaluateTaskState(requestId, System.currentTimeMillis());
        taskStore.put(requestId, state);

        CompletableFuture
                .supplyAsync(() -> writingEvaluateService.evaluate(request), executor)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        state.markSucceeded(result);
                    } else {
                        state.markFailed(extractError(throwable));
                    }
                });

        return state.toResponse("accepted");
    }

    @Override
    public WritingEvaluateTaskResponse getTask(String requestId) {
        cleanupExpired();
        EvaluateTaskState state = taskStore.get(requestId);
        if (state == null) {
            return null;
        }
        return state.toResponse(null);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        taskStore.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private String extractError(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return (message == null || message.isBlank()) ? "evaluate failed" : message;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class EvaluateTaskState {
        private final String requestId;
        private final long submittedAt;
        private volatile String status;
        private volatile String error;
        private volatile Long completedAt;
        private volatile WritingEvaluateResponse result;

        private EvaluateTaskState(String requestId, long submittedAt) {
            this.requestId = requestId;
            this.submittedAt = submittedAt;
            this.status = STATUS_PROCESSING;
        }

        private void markSucceeded(WritingEvaluateResponse result) {
            this.result = result;
            this.status = STATUS_SUCCEEDED;
            this.completedAt = System.currentTimeMillis();
        }

        private void markFailed(String error) {
            this.error = error;
            this.status = STATUS_FAILED;
            this.completedAt = System.currentTimeMillis();
        }

        private boolean isExpired(long now) {
            if (completedAt == null) {
                return false;
            }
            return now - completedAt > RETAIN_MS;
        }

        private WritingEvaluateTaskResponse toResponse(String defaultMessage) {
            WritingEvaluateTaskResponse response = new WritingEvaluateTaskResponse();
            response.setRequestId(requestId);
            response.setStatus(status);
            response.setMessage(defaultMessage);
            response.setError(error);
            response.setSubmittedAt(submittedAt);
            response.setCompletedAt(completedAt);
            response.setResult(result);
            return response;
        }
    }
}
