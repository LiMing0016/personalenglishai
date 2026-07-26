package com.personalenglishai.backend.service.subscription;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import com.personalenglishai.backend.mapper.subscription.AiTokenUsageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiUsageActivityServiceTest {
    private AiTokenUsageMapper mapper;
    private AiUsageActivityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiTokenUsageMapper.class);
        service = new AiUsageActivityService(mapper, new AiUsageProductClassifier());
    }

    @Test
    void groupsUtcEventsIntoRequestedTimezoneAndProduct() {
        when(mapper.selectEventsByUserAndOccurredAt(eq(7L), any(), any()))
                .thenReturn(List.of(
                        event("a", 7L, "assistant.conversation", 30L,
                                LocalDateTime.of(2026, 7, 25, 16, 30)),
                        event("b", 7L, "writing.translate", 20L,
                                LocalDateTime.of(2026, 7, 26, 4, 0)),
                        event("c", 7L, "future.capability", 5L,
                                LocalDateTime.of(2026, 7, 26, 5, 0))));

        var response = service.getActivity(
                7L,
                "ai_tokens",
                "day",
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 26),
                "Asia/Shanghai");

        assertThat(response.total()).isEqualTo(55L);
        assertThat(response.buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.date()).isEqualTo(LocalDate.of(2026, 7, 26));
            assertThat(bucket.total()).isEqualTo(55L);
            assertThat(bucket.byProduct()).containsEntry("assistant", 30L);
            assertThat(bucket.byProduct()).containsEntry("translation", 20L);
            assertThat(bucket.byProduct()).containsEntry("other", 5L);
        });
    }

    @Test
    void convertsRequestedNaturalDaysToHalfOpenUtcRange() {
        when(mapper.selectEventsByUserAndOccurredAt(eq(7L), any(), any())).thenReturn(List.of());

        service.getActivity(
                7L,
                "ai_tokens",
                "day",
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 26),
                "Asia/Shanghai");

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).selectEventsByUserAndOccurredAt(eq(7L), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 7, 25, 16, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 7, 26, 16, 0));
    }

    @Test
    void returnsSparseOrderedBucketsAndIgnoresNonPositiveEvents() {
        when(mapper.selectEventsByUserAndOccurredAt(eq(7L), any(), any()))
                .thenReturn(List.of(
                        event("late", 7L, "writing.evaluate", 9L,
                                LocalDateTime.of(2026, 7, 26, 2, 0)),
                        event("zero", 7L, "writing.evaluate", 0L,
                                LocalDateTime.of(2026, 7, 25, 2, 0)),
                        event("early", 7L, "vocabulary.import_analysis", 3L,
                                LocalDateTime.of(2026, 7, 24, 2, 0))));

        var response = service.getActivity(
                7L, "ai_tokens", "day",
                LocalDate.of(2026, 7, 24), LocalDate.of(2026, 7, 27), "UTC");

        assertThat(response.buckets()).extracting(bucket -> bucket.date().toString())
                .containsExactly("2026-07-24", "2026-07-26");
        assertThat(response.total()).isEqualTo(12L);
    }

    @Test
    void rejectsUnsupportedMetricGranularityRangeAndTimezone() {
        assertThatThrownBy(() -> service.getActivity(
                7L, "audio_seconds", "day",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 26), "UTC"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getActivity(
                7L, "ai_tokens", "week",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 26), "UTC"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getActivity(
                7L, "ai_tokens", "day",
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 7, 26), "UTC"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getActivity(
                7L, "ai_tokens", "day",
                LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 26), "UTC"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getActivity(
                7L, "ai_tokens", "day",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 26), "Mars/Base"))
                .isInstanceOf(BizException.class);
    }

    private static AiTokenUsageEvent event(
            String id,
            Long userId,
            String featureKey,
            Long totalTokens,
            LocalDateTime occurredAt) {
        AiTokenUsageEvent event = new AiTokenUsageEvent();
        event.setUsageEventId(id);
        event.setUserId(userId);
        event.setFeatureKey(featureKey);
        event.setTotalTokens(totalTokens);
        event.setOccurredAt(occurredAt);
        return event;
    }
}
