package com.personalenglishai.backend.service.subscription;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import com.personalenglishai.backend.entity.subscription.SubscriptionRedeemCode;
import com.personalenglishai.backend.entity.subscription.SubscriptionRedeemEvent;
import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import com.personalenglishai.backend.entity.subscription.UserAiTokenUsageMonthly;
import com.personalenglishai.backend.entity.subscription.UserSubscription;
import com.personalenglishai.backend.mapper.subscription.AiTokenUsageMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionRedeemCodeMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionPlanMapper;
import com.personalenglishai.backend.mapper.subscription.UserSubscriptionMapper;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private FakeSubscriptionRedeemCodeMapper redeemCodeMapper;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        planMapper = new FakeSubscriptionPlanMapper();
        subscriptionMapper = new FakeUserSubscriptionMapper();
        usageMapper = new FakeAiTokenUsageMapper();
        redeemCodeMapper = new FakeSubscriptionRedeemCodeMapper();
        service = new SubscriptionService(planMapper, subscriptionMapper, usageMapper, redeemCodeMapper, FIXED_CLOCK, "test-secret");
    }

    @Test
    void defaultUserUsesFreePlanAndDailyQuota() {
        var status = service.getCurrentSubscription(1L);

        assertThat(status.getPlanCode()).isEqualTo("free");
        assertThat(status.getQuotaPeriod()).isEqualTo("daily");
        assertThat(status.getDailyTokenLimit()).isEqualTo(10_000L);
        assertThat(status.getTokenLimit()).isEqualTo(10_000L);
        assertThat(status.getUsageDate()).isEqualTo(LocalDate.now(FIXED_CLOCK));
        assertThat(status.getTokenUsed()).isZero();
        assertThat(status.getTokenRemaining()).isEqualTo(10_000L);
    }

    @Test
    void mockPurchaseCreatesPaidPlanForThirtyDays() {
        var status = service.mockPurchase(1L, "basic");

        assertThat(status.getPlanCode()).isEqualTo("basic");
        assertThat(status.getQuotaPeriod()).isEqualTo("monthly");
        assertThat(status.getMonthlyTokenLimit()).isEqualTo(1_000_000L);
        assertThat(status.getTokenLimit()).isEqualTo(1_000_000L);
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
    void generatedRedeemCodeStoresHashOnlyAndCanBeRedeemed() {
        var generated = service.createRedeemCodes(99L, new CreateRedeemCodesRequest(
                "pro", 30, 1, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "beta"
        ));

        String plainCode = generated.getCodes().get(0).getCode();
        assertThat(plainCode).matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
        assertThat(redeemCodeMapper.codes.values()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getCodeHash()).isNotEqualTo(plainCode);
                    assertThat(row.getCodeHash()).hasSize(64);
                    assertThat(row.getPlanCode()).isEqualTo("pro");
                    assertThat(row.getDurationDays()).isEqualTo(30);
                    assertThat(row.getStatus()).isEqualTo("unused");
                });

        var status = service.redeemCode(1L, plainCode, "127.0.0.1");

        assertThat(status.getPlanCode()).isEqualTo("pro");
        assertThat(status.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusDays(30));
        assertThat(redeemCodeMapper.events).singleElement()
                .satisfies(event -> {
                    assertThat(event.getUserId()).isEqualTo(1L);
                    assertThat(event.getPlanCode()).isEqualTo("pro");
                    assertThat(event.getBeforePlanCode()).isEqualTo("free");
                    assertThat(event.getAfterPlanCode()).isEqualTo("pro");
                    assertThat(event.getRedeemIp()).isEqualTo("127.0.0.1");
                });
    }

    @Test
    void redeemingSamePlanExtendsFromCurrentPeriodEnd() {
        service.mockPurchase(1L, "basic");
        String code = issueCode("basic", 90, "unused", LocalDateTime.now(FIXED_CLOCK).plusDays(7));

        var status = service.redeemCode(1L, code, null);

        assertThat(status.getPlanCode()).isEqualTo("basic");
        assertThat(status.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusDays(120));
    }

    @Test
    void redeemingDifferentPlanSwitchesImmediately() {
        service.mockPurchase(1L, "basic");
        String code = issueCode("premium", 30, "unused", LocalDateTime.now(FIXED_CLOCK).plusDays(7));

        var status = service.redeemCode(1L, code, null);

        assertThat(status.getPlanCode()).isEqualTo("premium");
        assertThat(status.getCurrentPeriodStart()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        assertThat(status.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusDays(30));
    }

    @Test
    void redeemCodeRejectsInvalidExpiredRevokedAndUsedCodes() {
        assertThatThrownBy(() -> service.redeemCode(1L, "NOPE-NOPE-NOPE-NOPE", null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_VALIDATION_ERROR);

        String expired = issueCode("basic", 30, "unused", LocalDateTime.now(FIXED_CLOCK).minusSeconds(1));
        assertThatThrownBy(() -> service.redeemCode(1L, expired, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已过期");

        String revoked = issueCode("basic", 30, "revoked", LocalDateTime.now(FIXED_CLOCK).plusDays(7));
        assertThatThrownBy(() -> service.redeemCode(1L, revoked, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已撤销");

        String used = issueCode("basic", 30, "redeemed", LocalDateTime.now(FIXED_CLOCK).plusDays(7));
        assertThatThrownBy(() -> service.redeemCode(1L, used, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已使用");
    }

    @Test
    void redeemCodeCanOnlyBeConsumedOnce() {
        String code = issueCode("pro", 30, "unused", LocalDateTime.now(FIXED_CLOCK).plusDays(7));

        service.redeemCode(1L, code, null);

        assertThatThrownBy(() -> service.redeemCode(2L, code, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已使用");
        assertThat(redeemCodeMapper.events).hasSize(1);
    }

    @Test
    void recordsFreeUsageIntoDailyAggregateIdempotently() {
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
        assertThat(status.getQuotaPeriod()).isEqualTo("daily");
        assertThat(status.getTokenUsed()).isEqualTo(145L);
        assertThat(status.getTokenRemaining()).isEqualTo(9_855L);
        assertThat(usageMapper.monthly).isEmpty();
    }

    @Test
    void paidUsersStillRecordUsageIntoMonthlyAggregate() {
        service.mockPurchase(1L, "basic");

        assertThat(service.recordUsage(new AiTokenUsageRecord(
                "usage-paid-1",
                1L,
                "writing.evaluate",
                "openai",
                "gpt-4o",
                100L,
                0L,
                20L,
                0L,
                null,
                "trace-paid-1"
        ))).isTrue();

        var status = service.getCurrentSubscription(1L);
        assertThat(status.getQuotaPeriod()).isEqualTo("monthly");
        assertThat(status.getTokenUsed()).isEqualTo(120L);
        assertThat(status.getTokenRemaining()).isEqualTo(999_880L);
        assertThat(usageMapper.daily).isEmpty();
    }

    @Test
    void expiredPaidSubscriptionFallsBackToFreeDailyQuota() {
        service.mockPurchase(1L, "basic");
        subscriptionMapper.subscriptions.get(1L).setCurrentPeriodEnd(LocalDateTime.now(FIXED_CLOCK).minusSeconds(1));

        assertThat(service.recordUsage(new AiTokenUsageRecord(
                "usage-expired-1", 1L, "writing.evaluate", "openai", "gpt-4o",
                300L, 0L, 0L, 0L, null, "trace-expired-1"
        ))).isTrue();

        var status = service.getCurrentSubscription(1L);
        assertThat(status.getPlanCode()).isEqualTo("free");
        assertThat(status.getQuotaPeriod()).isEqualTo("daily");
        assertThat(status.getTokenUsed()).isEqualTo(300L);
        assertThat(usageMapper.monthly).isEmpty();
    }

    @Test
    void updatedFreeDailyQuotaImmediatelyAffectsQuotaCheck() {
        planMapper.updateQuotaRule("free", 120L, 100_000L);
        service.recordUsage(new AiTokenUsageRecord(
                "usage-free-limit-1", 1L, "writing.evaluate", "openai", "gpt-4o",
                120L, 0L, 0L, 0L, null, "trace-free-limit-1"
        ));

        assertThat(service.getCurrentSubscription(1L).getTokenLimit()).isEqualTo(120L);
        assertThatThrownBy(() -> service.assertAiTokenQuotaAvailable(1L))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED);
    }

    @Test
    void quotaCheckAllowsCurrentRequestUntilAlreadyOverLimit() {
        service.recordUsage(new AiTokenUsageRecord(
                "usage-1", 1L, "writing.evaluate", "openai", "gpt-4o",
                9_999L, 0L, 0L, 0L, null, "trace-1"));

        service.assertAiTokenQuotaAvailable(1L);

        service.recordUsage(new AiTokenUsageRecord(
                "usage-2", 1L, "writing.evaluate", "openai", "gpt-4o",
                2L, 0L, 0L, 0L, null, "trace-2"));

        assertThatThrownBy(() -> service.assertAiTokenQuotaAvailable(1L))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED);
    }

    private String issueCode(String planCode, int durationDays, String status, LocalDateTime expiresAt) {
        var generated = service.createRedeemCodes(99L, new CreateRedeemCodesRequest(
                planCode, durationDays, 1, LocalDateTime.now(FIXED_CLOCK).plusDays(7), "test"
        ));
        String plainCode = generated.getCodes().get(0).getCode();
        SubscriptionRedeemCode row = redeemCodeMapper.findByCodeHash(service.hashRedeemCodeForTest(plainCode));
        row.setStatus(status);
        row.setExpiresAt(expiresAt);
        return plainCode;
    }

    private static final class FakeSubscriptionPlanMapper implements SubscriptionPlanMapper {
        private final Map<String, SubscriptionPlan> plans = new LinkedHashMap<>();

        private FakeSubscriptionPlanMapper() {
            add("free", "Free", 100_000L, 10_000L, 0);
            add("basic", "Basic", 1_000_000L, null, 1);
            add("pro", "Pro", 5_000_000L, null, 2);
            add("premium", "Premium", 20_000_000L, null, 3);
        }

        private void add(String code, String name, long monthlyLimit, Long dailyLimit, int sortOrder) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setPlanCode(code);
            plan.setName(name);
            plan.setMonthlyTokenLimit(monthlyLimit);
            plan.setDailyTokenLimit(dailyLimit);
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

        @Override
        public int updateQuotaRule(String planCode, Long dailyTokenLimit, Long monthlyTokenLimit) {
            SubscriptionPlan plan = plans.get(planCode);
            if (plan == null) {
                return 0;
            }
            plan.setDailyTokenLimit(dailyTokenLimit);
            plan.setMonthlyTokenLimit(monthlyTokenLimit);
            return 1;
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
        private final Map<String, Long> daily = new LinkedHashMap<>();

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

        @Override
        public int upsertDailyUsage(Long userId, LocalDate usageDate, Long tokenDelta) {
            String key = userId + ":" + usageDate;
            daily.put(key, daily.getOrDefault(key, 0L) + tokenDelta);
            return 1;
        }

        @Override
        public Long selectDailyTokenUsed(Long userId, LocalDate usageDate) {
            return daily.get(userId + ":" + usageDate);
        }
    }

    private static final class FakeSubscriptionRedeemCodeMapper implements SubscriptionRedeemCodeMapper {
        private final Map<Long, SubscriptionRedeemCode> codes = new LinkedHashMap<>();
        private final Map<String, SubscriptionRedeemCode> codesByHash = new LinkedHashMap<>();
        private final List<SubscriptionRedeemEvent> events = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public int insertCode(SubscriptionRedeemCode code) {
            code.setId(nextId++);
            codes.put(code.getId(), code);
            codesByHash.put(code.getCodeHash(), code);
            return 1;
        }

        @Override
        public SubscriptionRedeemCode findByCodeHash(String codeHash) {
            return codesByHash.get(codeHash);
        }

        @Override
        public int markRedeemed(Long id, Long redeemedByUserId, LocalDateTime redeemedAt) {
            SubscriptionRedeemCode code = codes.get(id);
            if (code == null || !"unused".equals(code.getStatus())) {
                return 0;
            }
            code.setStatus("redeemed");
            code.setRedeemedByUserId(redeemedByUserId);
            code.setRedeemedAt(redeemedAt);
            return 1;
        }

        @Override
        public int insertEvent(SubscriptionRedeemEvent event) {
            events.add(event);
            return 1;
        }
    }
}
