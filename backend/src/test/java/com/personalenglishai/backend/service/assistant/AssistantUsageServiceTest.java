package com.personalenglishai.backend.service.assistant;

import com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse;
import com.personalenglishai.backend.service.subscription.AiTokenUsageRecord;
import com.personalenglishai.backend.service.subscription.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AssistantUsageServiceTest {

    @Test
    void recordsRunUsageWithStableRunId() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        AssistantUsageService service = new AssistantUsageService(subscriptionService);

        service.record(7L, run("run-42", "trace-42", "gpt-5", 80, 20, 30, 90));

        ArgumentCaptor<AiTokenUsageRecord> captor = ArgumentCaptor.forClass(AiTokenUsageRecord.class);
        verify(subscriptionService).recordUsage(captor.capture());
        assertThat(captor.getValue().usageEventId()).isEqualTo("assistant:run-42");
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().featureKey()).isEqualTo("assistant.conversation");
        assertThat(captor.getValue().provider()).isEqualTo("openai");
        assertThat(captor.getValue().model()).isEqualTo("gpt-5");
        assertThat(captor.getValue().inputTokens()).isEqualTo(80L);
        assertThat(captor.getValue().cachedInputTokens()).isEqualTo(20L);
        assertThat(captor.getValue().outputTokens()).isEqualTo(30L);
        assertThat(captor.getValue().totalTokens()).isEqualTo(90L);
        assertThat(captor.getValue().traceId()).isEqualTo("trace-42");
    }

    @Test
    void skipsMissingUsageWithoutInventingTokens() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        AssistantUsageService service = new AssistantUsageService(subscriptionService);
        AssistantRunMetadataResponse run = new AssistantRunMetadataResponse();
        run.setRunId("run-42");

        service.record(7L, run);

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void delegatesQuotaCheckBeforeAssistantRun() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        AssistantUsageService service = new AssistantUsageService(subscriptionService);

        service.assertQuota(7L);

        verify(subscriptionService).assertAiTokenQuotaAvailable(7L);
    }

    private static AssistantRunMetadataResponse run(
            String runId,
            String traceId,
            String model,
            int input,
            int cachedInput,
            int output,
            int total) {
        AssistantRunMetadataResponse.Usage usage = new AssistantRunMetadataResponse.Usage();
        usage.setInputTokens(input);
        usage.setCachedInputTokens(cachedInput);
        usage.setOutputTokens(output);
        usage.setTotalTokens(total);
        AssistantRunMetadataResponse run = new AssistantRunMetadataResponse();
        run.setRunId(runId);
        run.setTraceId(traceId);
        run.setModel(model);
        run.setUsage(usage);
        return run;
    }
}
