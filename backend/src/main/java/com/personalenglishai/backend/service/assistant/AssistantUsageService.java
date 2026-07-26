package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse;
import com.personalenglishai.backend.service.subscription.AiTokenUsageRecord;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantUsageService {
    private static final Logger log = LoggerFactory.getLogger(AssistantUsageService.class);
    private static final String FEATURE_KEY = "assistant.conversation";

    private final SubscriptionService subscriptionService;

    public AssistantUsageService(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public void assertQuota(Long userId) {
        subscriptionService.assertAiTokenQuotaAvailable(userId);
    }

    public void record(Long userId, AssistantRunMetadataResponse run) {
        if (userId == null
                || run == null
                || run.getRunId() == null
                || run.getRunId().isBlank()
                || run.getUsage() == null) {
            return;
        }
        AssistantRunMetadataResponse.Usage usage = run.getUsage();
        try {
            subscriptionService.recordUsage(new AiTokenUsageRecord(
                    "assistant:" + run.getRunId(),
                    userId,
                    FEATURE_KEY,
                    "openai",
                    run.getModel(),
                    toLong(usage.getInputTokens()),
                    toLong(usage.getCachedInputTokens()),
                    toLong(usage.getOutputTokens()),
                    null,
                    toLong(usage.getTotalTokens()),
                    run.getTraceId()
            ));
        } catch (Exception exception) {
            log.warn(
                    "assistant usage record failed userId={} runId={} traceId={} reason={}",
                    userId,
                    run.getRunId(),
                    run.getTraceId(),
                    exception.getMessage());
        }
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
