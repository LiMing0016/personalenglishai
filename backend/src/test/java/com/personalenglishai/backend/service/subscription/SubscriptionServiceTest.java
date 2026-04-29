package com.personalenglishai.backend.service.subscription;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import com.personalenglishai.backend.entity.subscription.UserAiTokenUsageMonthly;
import com.personalenglishai.backend.entity.subscription.UserSubscription;
import com.personalenglishai.backend.mapper.subscription.AiTokenUsageMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionPlanMapper;
import com.personalenglishai.backend.mapper.subscription.UserSubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-04-29T10:15:30Z"),
            ZoneId.of("UTC")
    );

    private FakeSubscriptionPlanMapper planMapper;
    private FakeUserSubscriptionMapper subscriptionMapper;
    private FakeAiTokenUsageMapper usageMapper;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        planMapper = new FakeSubscriptionPlanMapper();
        subscriptionMapper = new FakeUserSubscriptionMapper();
        usageMapper = new FakeAiTokenUsageMapper();
        service = new SubscriptionService(planMapper, subscriptionMapper, usageMapper, FIXED_CLOCK);
    }

    @Test
    void defaultUserUsesFreePlanAndMonthlyQuota() {
        var status = service.getCurrentSubscription(1L);

        assertThat(status.getPlanCode()).isEqualTo("free");
        assertThat(status.getMonthlyTokenLimit()).isEqualTo(100_000L);
        assertThat(status.getTokenUsed()).isZero();
        assertThat(status.getTokenRemaining()).isEqualTo(100_000L);
    }

    @Test
    void mockPurchaseCreatesPaidPlanForThirtyDays() {
        var status = service.mockPurchase(1L, "basic");

        assertThat(status.getPlanCode()).isEqualTo("basic");
        assertThat(status.getMonthlyTokenLimit()).isEqualTo(1_000_000L);
        assertThat(status.getCurrentPeriodStart()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(status.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusDays(30));
    }

    @Test
    void samePlanPurchaseExtendsFromCurrentActiveEnd() {
        service.mockPurchase(1L, "pro");
        var renewed = service.mockPurchase(1L, "pro");

        assertThat(renewed.getPlanCode()).isEqualTo("pro");
        assertThat(renewed.getCurrentPeriodStart()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(renewed.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusDays(60));
    }

    @Test
    void switchingPlanStartsNewThirtyDayPeriodImmediately() {
        service.mockPurchase(1L, "basic");
        var switched = service.mockPurchase(1L, "premium");

        assertThat(switched.getPlanCode()).isEqualTo("premium");
        assertThat(switched.getCurrentPeriodStart()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(switched.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusDays(30));
    }

    @Test
    void recordsUsageIntoMonthlyAggregateIdempotently() {
        var usage = new AiTokenUsageRecord(
                "usage-1",
                1L,
                "writing.evaluate",
                "openai",
                "gpt-4o",
                100L,
                20L,
                40L,
                5L,
                null,
                "trace-1"
        );

        assertThat(service.recordUsage(usage)).isTrue();
        assertThat(service.recordUsage(usage)).isFalse();

        var status = service.getCurrentSubscription(1L);
        assertThat(status.getTokenUsed()).isEqualTo(145L);
        assertThat(status.getTokenRemaining()).isEqualTo(99_855L);
    }

    @Test
    void quotaCheckAllowsCurrentRequestUntilAlreadyOverLimit() {
        service.recordUsage(new AiTokenUsageRecord(
                "usage-1", 1L, "writing.evaluate", "openai", "gpt-4o",
                99_999L, 0L, 0L, 0L, null, "trace-1"));

        service.assertAiTokenQuotaAvailable(1L);

        service.recordUsage(new AiTokenUsageRecord(
                "usage-2", 1L, "writing.evaluate", "openai", "gpt-4o",
                2L, 0L, 0L, 0L, null, "trace-2"));

        assertThatThrownBy(() -> service.assertAiTokenQuotaAvailable(1L))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED);
    }

    private static final class FakeSubscriptionPlanMapper implements SubscriptionPlanMapper {
        private final Map<String, SubscriptionPlan> plans = new LinkedHashMap<>();

        private FakeSubscriptionPlanMapper() {
            add("free", "Free", 100_000L, 0);
            add("basic", "Basic", 1_000_000L, 1);
            add("pro", "Pro", 5_000_000L, 2);
            add("premium", "Premium", 20_000_000L, 3);
        }

        private void add(String code, String name, long limit, int sortOrder) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setPlanCode(code);
            plan.setName(name);
            plan.setMonthlyTokenLimit(limit);
            plan.setSortOrder(sortOrder);
            plans.put(code, plan);
        }

        @Override
        public List<SubscriptionPlan> selectActivePlans() {
            return new ArrayList<>(plans.values());
        }

        @Override
        public SubscriptionPlan findByPlanCode(String planCode) {
            return plans.get(planCode);
        }
    }

    private static final class FakeUserSubscriptionMapper implements UserSubscriptionMapper {
        private final Map<Long, UserSubscription> subscriptions = new LinkedHashMap<>();

        @Override
        public UserSubscription findLatestByUserId(Long userId) {
            return subscriptions.get(userId);
        }

        @Override
        public int upsert(UserSubscription subscription) {
            subscriptions.put(subscription.getUserId(), subscription);
            return 1;
        }
    }

    private static final class FakeAiTokenUsageMapper implements AiTokenUsageMapper {
        private final Map<String, AiTokenUsageEvent> events = new LinkedHashMap<>();
        private final Map<String, UserAiTokenUsageMonthly> monthly = new LinkedHashMap<>();

        @Override
        public int insertIgnoreEvent(AiTokenUsageEvent event) {
            if (events.containsKey(event.getUsageEventId())) {
                return 0;
            }
            events.put(event.getUsageEventId(), event);
            return 1;
        }

        @Override
        public int upsertMonthlyUsage(Long userId, String usageMonth, Long tokenDelta) {
            String key = userId + ":" + usageMonth;
            UserAiTokenUsageMonthly row = monthly.computeIfAbsent(key, ignored -> {
                UserAiTokenUsageMonthly created = new UserAiTokenUsageMonthly();
                created.setUserId(userId);
                created.setUsageMonth(usageMonth);
                created.setTokenUsed(0L);
                return created;
            });
            row.setTokenUsed(row.getTokenUsed() + tokenDelta);
            return 1;
        }

        @Override
        public Long selectMonthlyTokenUsed(Long userId, String usageMonth) {
            UserAiTokenUsageMonthly row = monthly.get(userId + ":" + usageMonth);
            return row == null ? null : row.getTokenUsed();
        }
    }
}
