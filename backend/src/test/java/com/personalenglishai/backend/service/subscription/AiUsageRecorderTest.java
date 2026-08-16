package com.personalenglishai.backend.service.subscription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiUsageRecorderTest {

    @Test
    void providerTotalWinsOverComponentSum() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        AiUsageRecorder recorder = new AiUsageRecorder(subscriptionService);

        AiUsageContextHolder.call(
                new AiUsageContext(7L, "writing.evaluate", "trace-1"),
                () -> {
                    recorder.recordCurrentContext(
                            "openai", "gpt-5", "resp-1",
                            100, 20, 40, 30, 150);
                    return null;
                });

        ArgumentCaptor<AiTokenUsageRecord> captor = ArgumentCaptor.forClass(AiTokenUsageRecord.class);
        verify(subscriptionService).recordUsage(captor.capture());
        assertThat(captor.getValue().totalTokens()).isEqualTo(150L);
    }

    @Test
    void missingProviderTotalFallsBackToInputAndOutputWithoutDoubleCountingReasoning() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        AiUsageRecorder recorder = new AiUsageRecorder(subscriptionService);

        AiUsageContextHolder.call(
                new AiUsageContext(7L, "writing.evaluate", "trace-1"),
                () -> {
                    recorder.recordCurrentContext(
                            "openai", "gpt-5", "resp-1",
                            100, 20, 40, 30, null);
                    return null;
                });

        ArgumentCaptor<AiTokenUsageRecord> captor = ArgumentCaptor.forClass(AiTokenUsageRecord.class);
        verify(subscriptionService).recordUsage(captor.capture());
        assertThat(captor.getValue().totalTokens()).isEqualTo(140L);
    }
}
