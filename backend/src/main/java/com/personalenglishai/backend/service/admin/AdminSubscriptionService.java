package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminSubscriptionQuotaRuleUpdateRequest;
import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import com.personalenglishai.backend.mapper.admin.AdminSubscriptionQueryMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionPlanMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminSubscriptionService {
    private static final String PLAN_FREE = "free";
    private static final String QUOTA_PERIOD_DAILY = "daily";
    private static final String QUOTA_PERIOD_MONTHLY = "monthly";

    private final AdminSubscriptionQueryMapper subscriptionQueryMapper;
    private final SubscriptionPlanMapper planMapper;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    @Autowired
    public AdminSubscriptionService(AdminSubscriptionQueryMapper subscriptionQueryMapper,
                                    SubscriptionPlanMapper planMapper,
                                    AdminAuditService adminAuditService) {
        this(subscriptionQueryMapper, planMapper, adminAuditService, Clock.systemDefaultZone());
    }

    AdminSubscriptionService(AdminSubscriptionQueryMapper subscriptionQueryMapper,
                             SubscriptionPlanMapper planMapper,
                             AdminAuditService adminAuditService,
                             Clock clock) {
        this.subscriptionQueryMapper = subscriptionQueryMapper;
        this.planMapper = planMapper;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    public AdminPageResponse<Map<String, Object>> listSubscriptions(String keyword,
                                                                    String planCode,
                                                                    String subscriptionStatus,
                                                                    Boolean overLimit,
                                                                    String expiresFrom,
                                                                    String expiresTo,
                                                                    int page,
                                                                    int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        LocalDate usageDate = LocalDate.now(clock);
        String usageMonth = YearMonth.now(clock).toString();
        LocalDateTime now = LocalDateTime.now(clock);
        String normalizedPlanCode = normalize(planCode);
        String normalizedStatus = normalize(subscriptionStatus);
        List<Map<String, Object>> items = subscriptionQueryMapper.searchSubscriptions(
                trimToNull(keyword),
                normalizedPlanCode,
                normalizedStatus,
                overLimit,
                trimToNull(expiresFrom),
                trimToNull(expiresTo),
                usageMonth,
                usageDate,
                now,
                offset,
                normalizedSize
        );
        long total = subscriptionQueryMapper.countSubscriptions(
                trimToNull(keyword),
                normalizedPlanCode,
                normalizedStatus,
                overLimit,
                trimToNull(expiresFrom),
                trimToNull(expiresTo),
                usageMonth,
                usageDate,
                now
        );
        return new AdminPageResponse<>(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> getOverview() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> overview = new LinkedHashMap<>(subscriptionQueryMapper.selectOverview(
                LocalDate.now(clock),
                YearMonth.now(clock).toString(),
                now
        ));
        overview.put("planDistribution", subscriptionQueryMapper.selectPlanDistribution(now));
        return overview;
    }

    public List<Map<String, Object>> listDailyStats(String dateFrom, String dateTo) {
        LocalDate today = LocalDate.now(clock);
        LocalDate normalizedTo = parseDateOrDefault(dateTo, today);
        LocalDate normalizedFrom = parseDateOrDefault(dateFrom, normalizedTo.minusDays(13));
        if (normalizedFrom.isAfter(normalizedTo)) {
            LocalDate tmp = normalizedFrom;
            normalizedFrom = normalizedTo;
            normalizedTo = tmp;
        }
        return subscriptionQueryMapper.selectDailyStats(normalizedFrom, normalizedTo);
    }

    public List<Map<String, Object>> listQuotaRules() {
        List<SubscriptionPlan> plans = planMapper.selectActivePlans();
        return plans.stream()
                .sorted((a, b) -> Integer.compare(nullToZero(a.getSortOrder()), nullToZero(b.getSortOrder())))
                .map(this::quotaRuleMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateQuotaRule(Long adminUserId,
                                               String rawPlanCode,
                                               AdminSubscriptionQuotaRuleUpdateRequest request,
                                               HttpServletRequest httpRequest) {
        String planCode = normalize(rawPlanCode);
        if (planCode == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "套餐不能为空");
        }
        SubscriptionPlan before = planMapper.findByPlanCode(planCode);
        if (before == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "无效的套餐");
        }

        Long nextDaily = before.getDailyTokenLimit();
        Long nextMonthly = before.getMonthlyTokenLimit();
        if (PLAN_FREE.equals(planCode)) {
            nextDaily = requirePositive(request == null ? null : request.getDailyTokenLimit(), "Free 每日额度必须大于 0");
        } else {
            nextMonthly = requirePositive(request == null ? null : request.getMonthlyTokenLimit(), "付费套餐月额度必须大于 0");
        }

        int updated = planMapper.updateQuotaRule(planCode, nextDaily, nextMonthly);
        if (updated <= 0) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "额度规则更新失败");
        }
        SubscriptionPlan after = planMapper.findByPlanCode(planCode);
        adminAuditService.audit(
                adminUserId,
                "UPDATE_SUBSCRIPTION_QUOTA_RULE",
                "subscription_plan",
                planCode,
                null,
                quotaRuleMap(before),
                quotaRuleMap(after),
                httpRequest
        );
        return quotaRuleMap(after);
    }

    private Map<String, Object> quotaRuleMap(SubscriptionPlan plan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("planCode", plan.getPlanCode());
        map.put("planName", plan.getName());
        map.put("quotaPeriod", PLAN_FREE.equalsIgnoreCase(plan.getPlanCode()) ? QUOTA_PERIOD_DAILY : QUOTA_PERIOD_MONTHLY);
        map.put("dailyTokenLimit", plan.getDailyTokenLimit());
        map.put("monthlyTokenLimit", plan.getMonthlyTokenLimit());
        map.put("active", plan.getActive());
        map.put("sortOrder", plan.getSortOrder());
        return map;
    }

    private static Long requirePositive(Long value, String message) {
        if (value == null || value <= 0L) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, message);
        }
        return value;
    }

    private static String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate parseDateOrDefault(String value, LocalDate fallback) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
