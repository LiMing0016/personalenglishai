package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.entity.EvaluateTask;
import com.personalenglishai.backend.mapper.EvaluateTaskMapper;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WritingEvaluateTaskServiceImpl implements WritingEvaluateTaskService {

    private static final Logger log = LoggerFactory.getLogger(WritingEvaluateTaskServiceImpl.class);
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_SUCCEEDED = "succeeded";
    private static final String STATUS_FAILED = "failed";
    private static final long RETAIN_MS = Duration.ofHours(6).toMillis();
    private static final long TASK_TIMEOUT_MS = Duration.ofMinutes(3).toMillis();
    private static final AtomicInteger WORKER_SEQ = new AtomicInteger(0);

    private final WritingEvaluateService writingEvaluateService;
    private final EvaluateTaskMapper evaluateTaskMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public WritingEvaluateTaskServiceImpl(WritingEvaluateService writingEvaluateService,
                                          EvaluateTaskMapper evaluateTaskMapper,
                                          ObjectMapper objectMapper,
                                          @Value("${writing.evaluate.thread-pool-size:4}") int threadPoolSize) {
        this.writingEvaluateService = writingEvaluateService;
        this.evaluateTaskMapper = evaluateTaskMapper;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(threadPoolSize, runnable -> {
            Thread t = new Thread(runnable, "writing-evaluate-worker-" + WORKER_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public WritingEvaluateTaskResponse submit(WritingEvaluateRequest request) {
        cleanupExpired();
        String requestId = "eval-task-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        EvaluateTask task = new EvaluateTask();
        task.setRequestId(requestId);
        task.setUserId(request.getUserId());
        task.setStatus(STATUS_PROCESSING);
        task.setSubmittedAt(System.currentTimeMillis());
        evaluateTaskMapper.insert(task);

        CompletableFuture
                .supplyAsync(() -> writingEvaluateService.evaluate(request), executor)
                .orTimeout(TASK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    try {
                        if (throwable == null) {
                            String json = objectMapper.writeValueAsString(result);
                            evaluateTaskMapper.updateStatus(requestId, STATUS_SUCCEEDED,
                                    null, json, System.currentTimeMillis());
                        } else {
                            String error = extractError(throwable);
                            evaluateTaskMapper.updateStatus(requestId, STATUS_FAILED,
                                    error, null, System.currentTimeMillis());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to persist task result. requestId={} reason={}", requestId, e.getMessage());
                        evaluateTaskMapper.updateStatus(requestId, STATUS_FAILED,
                                "internal error", null, System.currentTimeMillis());
                    }
                });

        WritingEvaluateTaskResponse response = new WritingEvaluateTaskResponse();
        response.setRequestId(requestId);
        response.setStatus(STATUS_PROCESSING);
        response.setMessage("accepted");
        response.setSubmittedAt(task.getSubmittedAt());
        return response;
    }

    @Override
    public WritingEvaluateTaskResponse getTask(String requestId) {
        EvaluateTask task = evaluateTaskMapper.selectByRequestId(requestId);
        if (task == null) {
            // Check for stuck processing tasks (timeout)
            return null;
        }

        // Auto-fail stuck tasks
        if (STATUS_PROCESSING.equals(task.getStatus())
                && System.currentTimeMillis() - task.getSubmittedAt() > TASK_TIMEOUT_MS) {
            evaluateTaskMapper.updateStatus(requestId, STATUS_FAILED,
                    "评估超时，请重新提交", null, System.currentTimeMillis());
            task.setStatus(STATUS_FAILED);
            task.setError("评估超时，请重新提交");
            task.setCompletedAt(System.currentTimeMillis());
        }

        WritingEvaluateTaskResponse response = new WritingEvaluateTaskResponse();
        response.setRequestId(task.getRequestId());
        response.setUserId(task.getUserId());
        response.setStatus(task.getStatus());
        response.setError(task.getError());
        response.setSubmittedAt(task.getSubmittedAt());
        response.setCompletedAt(task.getCompletedAt());

        if (STATUS_SUCCEEDED.equals(task.getStatus()) && task.getResultJson() != null) {
            try {
                response.setResult(objectMapper.readValue(task.getResultJson(), WritingEvaluateResponse.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize task result. requestId={}", requestId);
            }
        }
        return response;
    }

    private void cleanupExpired() {
        try {
            long cutoff = System.currentTimeMillis() - RETAIN_MS;
            evaluateTaskMapper.deleteExpired(cutoff);
        } catch (Exception e) {
            log.warn("cleanupExpired failed: {}", e.getMessage());
        }
    }

    private String extractError(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) return "evaluate failed";
        return message.length() > 450 ? message.substring(0, 450) : message;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
