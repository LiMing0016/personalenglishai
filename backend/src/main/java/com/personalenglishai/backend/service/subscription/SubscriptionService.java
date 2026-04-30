package com.personalenglishai.backend.service.subscription;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import com.personalenglishai.backend.entity.subscription.SubscriptionRedeemCode;
import com.personalenglishai.backend.entity.subscription.SubscriptionRedeemEvent;
import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import com.personalenglishai.backend.entity.subscription.UserSubscription;
import com.personalenglishai.backend.mapper.subscription.AiTokenUsageMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionRedeemCodeMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionPlanMapper;
import com.personalenglishai.backend.mapper.subscription.UserSubscriptionMapper;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesRequest;
import com.personalenglishai.backend.service.subscription.dto.CreateRedeemCodesResponse;
import com.personalenglishai.backend.service.subscription.dto.SubscriptionPlanResponse;
import com.personalenglishai.backend.service.subscription.dto.SubscriptionStatusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SubscriptionService {
    private static final String PLAN_FREE = "free";
    private static final String STATUS_ACTIVE = "active";
    private static final String REDEEM_STATUS_UNUSED = "unused";
    private static final String REDEEM_STATUS_REDEEMED = "redeemed";
    private static final String REDEEM_STATUS_REVOKED = "revoked";
    private static final String REDEEM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Map<String, SubscriptionPlan> DEFAULT_PLANS = defaultPlans();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SubscriptionPlanMapper planMapper;
    private final UserSubscriptionMapper subscriptionMapper;
    private final AiTokenUsageMapper usageMapper;
    private final SubscriptionRedeemCodeMapper redeemCodeMapper;
    private final Clock clock;
    private final String redeemHmacSecret;

    @Autowired
    public SubscriptionService(SubscriptionPlanMapper planMapper,
                               UserSubscriptionMapper subscriptionMapper,
                               AiTokenUsageMapper usageMapper,
                               SubscriptionRedeemCodeMapper redeemCodeMapper,
                               @Value("${subscription.redeem.hmac-secret:${JWT_SECRET:}}") String redeemHmacSecret) {
        this(planMapper, subscriptionMapper, usageMapper, redeemCodeMapper, Clock.systemDefaultZone(), redeemHmacSecret);
    }

    SubscriptionService(SubscriptionPlanMapper planMapper,
                        UserSubscriptionMapper subscriptionMapper,
                        AiTokenUsageMapper usageMapper,
                        SubscriptionRedeemCodeMapper redeemCodeMapper,
                        Clock clock,
                        String redeemHmacSecret) {
        this.planMapper = planMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.usageMapper = usageMapper;
        this.redeemCodeMapper = redeemCodeMapper;
        this.clock = clock;
        this.redeemHmacSecret = redeemHmacSecret == null ? "" : redeemHmacSecret;
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

        return applySubscription(userId, planCode, 30);
    }

    @Transactional
    public CreateRedeemCodesResponse createRedeemCodes(Long adminUserId, CreateRedeemCodesRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码参数不能为空");
        }
        String planCode = normalizePlanCode(request.getPlanCode());
        int durationDays = request.getDurationDays() == null ? 0 : request.getDurationDays();
        int count = request.getCount() == null ? 0 : request.getCount();
        LocalDateTime now = LocalDateTime.now(clock);
        if (PLAN_FREE.equals(planCode) || planByCode(planCode) == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "无效的会员档位");
        }
        if (durationDays <= 0 || count <= 0 || count > 500) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "无效的兑换码数量或有效天数");
        }
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(now)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码过期时间必须晚于当前时间");
        }

        CreateRedeemCodesResponse response = new CreateRedeemCodesResponse();
        response.setPlanCode(planCode);
        response.setDurationDays(durationDays);
        response.setExpiresAt(request.getExpiresAt());
        response.setBatchName(trimToNull(request.getBatchName()));
        List<CreateRedeemCodesResponse.RedeemCodeItem> codes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String plainCode = uniquePlainCode();
            SubscriptionRedeemCode row = new SubscriptionRedeemCode();
            row.setCodeHash(hashRedeemCode(plainCode));
            row.setPlanCode(planCode);
            row.setDurationDays(durationDays);
            row.setStatus(REDEEM_STATUS_UNUSED);
            row.setExpiresAt(request.getExpiresAt());
            row.setBatchName(trimToNull(request.getBatchName()));
            row.setCreatedByUserId(adminUserId);
            redeemCodeMapper.insertCode(row);
            codes.add(new CreateRedeemCodesResponse.RedeemCodeItem(plainCode));
        }
        response.setCodes(codes);
        return response;
    }

    @Transactional
    public SubscriptionStatusResponse redeemCode(Long userId, String rawCode, String redeemIp) {
        if (userId == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "请先登录后兑换会员码");
        }
        String codeHash = hashRedeemCode(rawCode);
        SubscriptionRedeemCode code = redeemCodeMapper.findByCodeHash(codeHash);
        if (code == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码无效");
        }
        validateRedeemCodeUsable(code);

        LocalDateTime now = LocalDateTime.now(clock);
        SubscriptionStatusResponse before = getCurrentSubscription(userId);
        int marked = redeemCodeMapper.markRedeemed(code.getId(), userId, now);
        if (marked <= 0) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码已使用");
        }

        SubscriptionStatusResponse after = applySubscription(userId, code.getPlanCode(), code.getDurationDays());
        SubscriptionRedeemEvent event = new SubscriptionRedeemEvent();
        event.setRedeemCodeId(code.getId());
        event.setUserId(userId);
        event.setPlanCode(code.getPlanCode());
        event.setDurationDays(code.getDurationDays());
        event.setBeforePlanCode(before.getPlanCode());
        event.setBeforePeriodEnd(before.getCurrentPeriodEnd());
        event.setAfterPlanCode(after.getPlanCode());
        event.setAfterPeriodEnd(after.getCurrentPeriodEnd());
        event.setRedeemIp(trimToNull(redeemIp));
        event.setRedeemedAt(now);
        redeemCodeMapper.insertEvent(event);
        return after;
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

    private SubscriptionStatusResponse applySubscription(Long userId, String planCode, int durationDays) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserSubscription current = resolveActiveSubscription(userId, now);
        LocalDateTime end = now.plusDays(durationDays);
        if (current != null && planCode.equalsIgnoreCase(current.getPlanCode())) {
            end = current.getCurrentPeriodEnd().plusDays(durationDays);
        }

        UserSubscription next = new UserSubscription();
        next.setUserId(userId);
        next.setPlanCode(planCode);
        next.setStatus(STATUS_ACTIVE);
        next.setCurrentPeriodStart(now);
        next.setCurrentPeriodEnd(end);
        subscriptionMapper.upsert(next);
        return getCurrentSubscription(userId);
    }

    private void validateRedeemCodeUsable(SubscriptionRedeemCode code) {
        String status = code.getStatus() == null ? "" : code.getStatus().toLowerCase(Locale.ROOT);
        if (REDEEM_STATUS_REDEEMED.equals(status)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码已使用");
        }
        if (REDEEM_STATUS_REVOKED.equals(status)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码已撤销");
        }
        if (!REDEEM_STATUS_UNUSED.equals(status)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码无效");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (code.getExpiresAt() != null && !code.getExpiresAt().isAfter(now)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码已过期");
        }
        if (PLAN_FREE.equals(normalizePlanCode(code.getPlanCode())) || planByCode(code.getPlanCode()) == null) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码档位无效");
        }
        if (code.getDurationDays() == null || code.getDurationDays() <= 0) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码有效天数无效");
        }
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

    private String uniquePlainCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = generatePlainCode();
            if (redeemCodeMapper.findByCodeHash(hashRedeemCode(code)) == null) {
                return code;
            }
        }
        throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "生成兑换码失败");
    }

    private static String generatePlainCode() {
        StringBuilder raw = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            raw.append(REDEEM_CODE_ALPHABET.charAt(RANDOM.nextInt(REDEEM_CODE_ALPHABET.length())));
        }
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private String hashRedeemCode(String rawCode) {
        String normalized = normalizeRedeemCode(rawCode);
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "兑换码不能为空");
        }
        if (redeemHmacSecret.isBlank()) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "兑换码密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(redeemHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "兑换码校验失败");
        }
    }

    String hashRedeemCodeForTest(String rawCode) {
        return hashRedeemCode(rawCode);
    }

    private static String normalizeRedeemCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
