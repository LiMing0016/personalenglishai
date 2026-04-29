package com.personalenglishai.backend.service.subscription;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import com.personalenglishai.backend.entity.subscription.UserSubscription;
import com.personalenglishai.backend.mapper.subscription.AiTokenUsageMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionPlanMapper;
import com.personalenglishai.backend.mapper.subscription.UserSubscriptionMapper;
import com.personalenglishai.backend.service.subscription.dto.SubscriptionPlanResponse;
import com.personalenglishai.backend.service.subscription.dto.SubscriptionStatusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SubscriptionService {
    private static final String PLAN_FREE = "free";
    private static final String STATUS_ACTIVE = "active";
    private static final Map<String, SubscriptionPlan> DEFAULT_PLANS = defaultPlans();

    private final SubscriptionPlanMapper planMapper;
    private final UserSubscriptionMapper subscriptionMapper;
    private final AiTokenUsageMapper usageMapper;
    private final Clock clock;

    @Autowired
    public SubscriptionService(SubscriptionPlanMapper planMapper,
                               UserSubscriptionMapper subscriptionMapper,
                               AiTokenUsageMapper usageMapper) {
        this(planMapper, subscriptionMapper, usageMapper, Clock.systemDefaultZone());
    }

    SubscriptionService(SubscriptionPlanMapper planMapper,
                        UserSubscriptionMapper subscriptionMapper,
                        AiTokenUsageMapper usageMapper,
                        Clock clock) {
        this.planMapper = planMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.usageMapper = usageMapper;
        this.clock = clock;
    }

    public List<SubscriptionPlanResponse> listPlans() {
        List<SubscriptionPlan> plans = planMapper.selectActivePlans();
        if (plans == null || plans.isEmpty()) {
            plans = DEFAULT_PLANS.values().stream().toList();
        }
        return plans.stream()
                .sorted((a, b) -> Integer.compare(nullToZero(a.getSortOrder()), nullToZero(b.getSortOrder())))
                .map(this::toPlanResponse)
                .toList();
    }

    public SubscriptionStatusResponse getCurrentSubscription(Long userId) {
        SubscriptionPlan plan = resolveCurrentPlan(userId);
        UserSubscription subscription = resolveActiveSubscription(userId, LocalDateTime.now(clock));
        String usageMonth = currentUsageMonth();
        long used = monthlyUsed(userId, usageMonth);
        long limit = plan.getMonthlyTokenLimit() == null ? 0L : plan.getMonthlyTokenLimit();

        SubscriptionStatusResponse response = new SubscriptionStatusResponse();
        response.setPlanCode(plan.getPlanCode());
        response.setPlanName(plan.getName());
        response.setCurrentPeriodStart(subscription == null ? null : subscription.getCurrentPeriodStart());
        response.setCurrentPeriodEnd(subscription == null ? null : subscription.getCurrentPeriodEnd());
        response.setUsageMonth(usageMonth);
        response.setMonthlyTokenLimit(limit);
        response.setTokenUsed(used);
        response.setTokenRemaining(Math.max(0L, limit - used));
        response.setOverLimit(used >= limit);
        return response;
    }

    @Transactional
    public SubscriptionStatusResponse mockPurchase(Long userId, String rawPlanCode) {
        String planCode = normalizePlanCode(rawPlanCode);
        if (userId == null || PLAN_FREE.equals(planCode)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "请选择 Basic、Pro 或 Premium");
        }
        SubscriptionPlan plan = planByCode(planCode);
        if (plan == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "无效的会员档位");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        UserSubscription current = resolveActiveSubscription(userId, now);
        LocalDateTime start = now;
        LocalDateTime end = now.plusDays(30);
        if (current != null && planCode.equalsIgnoreCase(current.getPlanCode())) {
            end = current.getCurrentPeriodEnd().plusDays(30);
        }

        UserSubscription next = new UserSubscription();
        next.setUserId(userId);
        next.setPlanCode(planCode);
        next.setStatus(STATUS_ACTIVE);
        next.setCurrentPeriodStart(start);
        next.setCurrentPeriodEnd(end);
        subscriptionMapper.upsert(next);
        return getCurrentSubscription(userId);
    }

    public void assertAiTokenQuotaAvailable(Long userId) {
        if (userId == null) {
            return;
        }
        SubscriptionStatusResponse status = getCurrentSubscription(userId);
        if (Boolean.TRUE.equals(status.getOverLimit())) {
            throw new BizException(
                    ErrorCode.SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED,
                    "本月 AI token 额度已用完，请升级会员后继续使用"
            );
        }
    }

    @Transactional
    public boolean recordUsage(AiTokenUsageRecord record) {
        if (record == null || record.userId() == null || isBlank(record.usageEventId())) {
            return false;
        }
        long totalTokens = resolveTotalTokens(record);
        if (totalTokens <= 0L) {
            return false;
        }

        AiTokenUsageEvent event = new AiTokenUsageEvent();
        event.setUsageEventId(record.usageEventId());
        event.setUserId(record.userId());
        event.setFeatureKey(record.featureKey());
        event.setProvider(record.provider());
        event.setModel(record.model());
        event.setInputTokens(defaultLong(record.inputTokens()));
        event.setCachedInputTokens(defaultLong(record.cachedInputTokens()));
        event.setOutputTokens(defaultLong(record.outputTokens()));
        event.setReasoningTokens(defaultLong(record.reasoningTokens()));
        event.setTotalTokens(totalTokens);
        event.setTraceId(record.traceId());
        event.setOccurredAt(LocalDateTime.now(clock));

        int inserted = usageMapper.insertIgnoreEvent(event);
        if (inserted <= 0) {
            return false;
        }
        usageMapper.upsertMonthlyUsage(record.userId(), currentUsageMonth(), totalTokens);
        return true;
    }

    private SubscriptionPlan resolveCurrentPlan(Long userId) {
        UserSubscription subscription = resolveActiveSubscription(userId, LocalDateTime.now(clock));
        if (subscription == null) {
            return DEFAULT_PLANS.get(PLAN_FREE);
        }
        SubscriptionPlan plan = planByCode(subscription.getPlanCode());
        return plan == null ? DEFAULT_PLANS.get(PLAN_FREE) : plan;
    }

    private UserSubscription resolveActiveSubscription(Long userId, LocalDateTime now) {
        if (userId == null) {
            return null;
        }
        UserSubscription subscription = subscriptionMapper.findLatestByUserId(userId);
        if (subscription == null || !STATUS_ACTIVE.equalsIgnoreCase(subscription.getStatus())) {
            return null;
        }
        LocalDateTime end = subscription.getCurrentPeriodEnd();
        return end != null && end.isAfter(now) ? subscription : null;
    }

    private SubscriptionPlan planByCode(String rawPlanCode) {
        String planCode = normalizePlanCode(rawPlanCode);
        SubscriptionPlan plan = planMapper.findByPlanCode(planCode);
        return plan == null ? DEFAULT_PLANS.get(planCode) : plan;
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(plan.getPlanCode(), plan.getName(), plan.getMonthlyTokenLimit());
    }

    private long monthlyUsed(Long userId, String usageMonth) {
        if (userId == null) {
            return 0L;
        }
        Long used = usageMapper.selectMonthlyTokenUsed(userId, usageMonth);
        return used == null ? 0L : used;
    }

    private String currentUsageMonth() {
        return YearMonth.now(clock).toString();
    }

    private static long resolveTotalTokens(AiTokenUsageRecord record) {
        if (record.totalTokens() != null && record.totalTokens() > 0L) {
            return record.totalTokens();
        }
        return defaultLong(record.inputTokens())
                + defaultLong(record.outputTokens())
                + defaultLong(record.reasoningTokens());
    }

    private static Long defaultLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String normalizePlanCode(String planCode) {
        return planCode == null ? "" : planCode.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, SubscriptionPlan> defaultPlans() {
        Map<String, SubscriptionPlan> plans = new LinkedHashMap<>();
        putPlan(plans, "free", "Free", 100_000L, 0);
        putPlan(plans, "basic", "Basic", 1_000_000L, 1);
        putPlan(plans, "pro", "Pro", 5_000_000L, 2);
        putPlan(plans, "premium", "Premium", 20_000_000L, 3);
        return plans;
    }

    private static void putPlan(Map<String, SubscriptionPlan> plans, String code, String name, long limit, int sortOrder) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode(code);
        plan.setName(name);
        plan.setMonthlyTokenLimit(limit);
        plan.setSortOrder(sortOrder);
        plan.setActive(true);
        plans.put(code, plan);
    }
}
