package com.personalenglishai.backend.service.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiUsageRecorder {
    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);

    private final SubscriptionService subscriptionService;

    public AiUsageRecorder(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public void recordCurrentContext(String provider,
                                     String model,
                                     String providerRequestId,
                                     Integer inputTokens,
                                     Integer cachedInputTokens,
                                     Integer outputTokens,
                                     Integer reasoningTokens,
                                     Integer totalTokens) {
        AiUsageContext context = AiUsageContextHolder.current();
        if (context == null || context.userId() == null) {
            return;
        }
        String usageEventId = usageEventId(provider, model, providerRequestId, context.traceId());
        Long normalizedTotalTokens = totalTokens(inputTokens, outputTokens, reasoningTokens, totalTokens);
        try {
            subscriptionService.recordUsage(new AiTokenUsageRecord(
                    usageEventId,
                    context.userId(),
                    context.featureKey(),
                    provider,
                    model,
                    toLong(inputTokens),
                    toLong(cachedInputTokens),
                    toLong(outputTokens),
                    toLong(reasoningTokens),
                    normalizedTotalTokens,
                    context.traceId()
            ));
        } catch (Exception e) {
            log.warn("AI usage record failed userId={} featureKey={} provider={} model={} reason={}",
                    context.userId(), context.featureKey(), provider, model, e.getMessage());
        }
    }

    private String usageEventId(String provider, String model, String providerRequestId, String traceId) {
        if (providerRequestId != null && !providerRequestId.isBlank()) {
            return provider + ":" + providerRequestId;
        }
        return "local:" + UUID.randomUUID();
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static Long totalTokens(Integer inputTokens,
                                    Integer outputTokens,
                                    Integer reasoningTokens,
                                    Integer providerTotalTokens) {
        if (inputTokens != null || outputTokens != null || reasoningTokens != null) {
            return (long) defaultInt(inputTokens) + defaultInt(outputTokens) + defaultInt(reasoningTokens);
        }
        return toLong(providerTotalTokens);
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
