package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.dto.admin.AdminMeResponse;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminUserRolesUpdateRequest;
import com.personalenglishai.backend.dto.admin.AdminUserStatusUpdateRequest;
import com.personalenglishai.backend.dto.writing.EvaluationHistoryResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.entity.UserProfile;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.mapper.UserProfileMapper;
import com.personalenglishai.backend.mapper.admin.AdminUserQueryMapper;
import com.personalenglishai.backend.mapper.admin.AdminUserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminUserService {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminAuditService adminAuditService;
    private final AdminUserQueryMapper adminUserQueryMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserAbilityProfileMapper userAbilityProfileMapper;
    private final EssayEvaluationMapper essayEvaluationMapper;

    public AdminUserService(AdminAuthorizationService adminAuthorizationService,
                            AdminAuditService adminAuditService,
                            AdminUserQueryMapper adminUserQueryMapper,
                            AdminUserRoleMapper adminUserRoleMapper,
                            UserMapper userMapper,
                            UserProfileMapper userProfileMapper,
                            UserAbilityProfileMapper userAbilityProfileMapper,
                            EssayEvaluationMapper essayEvaluationMapper) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.adminAuditService = adminAuditService;
        this.adminUserQueryMapper = adminUserQueryMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.userAbilityProfileMapper = userAbilityProfileMapper;
        this.essayEvaluationMapper = essayEvaluationMapper;
    }

    public AdminMeResponse getMe(Long adminUserId) {
        User user = adminAuthorizationService.requireAdmin(adminUserId);
        List<String> roles = adminAuthorizationService.getRoles(adminUserId);
        Set<String> permissions = adminAuthorizationService.getPermissions(adminUserId);
        AdminMeResponse response = new AdminMeResponse();
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setNickname(user.getNickname());
        response.setRoles(roles);
        response.setPermissions(new ArrayList<>(permissions));
        return response;
    }

    public AdminPageResponse<Map<String, Object>> listUsers(Long userId,
                                                            String keyword, String status, String role, String registerSource,
                                                            String adminRole, String studyStage,
                                                            String lastActiveFrom, String lastActiveTo,
                                                            String createdFrom, String createdTo,
                                                            String planCode, String subscriptionStatus,
                                                            Boolean overLimit,
                                                            int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String usageMonth = YearMonth.from(today).toString();
        List<Map<String, Object>> items = adminUserQueryMapper.searchUsers(userId, keyword, status, role, registerSource, adminRole,
                studyStage, lastActiveFrom, lastActiveTo, createdFrom, createdTo, planCode, subscriptionStatus,
                overLimit, now, usageMonth, today, offset, normalizedSize);
        for (Map<String, Object> item : items) {
            Object csv = item.remove("adminRolesCsv");
            item.put("adminRoles", csv == null || String.valueOf(csv).isBlank()
                    ? List.of() : List.of(String.valueOf(csv).split(",")));
        }
        long total = adminUserQueryMapper.countUsers(userId, keyword, status, role, registerSource, adminRole, studyStage,
                lastActiveFrom, lastActiveTo, createdFrom, createdTo, planCode, subscriptionStatus, overLimit,
                now, usageMonth, today);
        return new AdminPageResponse<>(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> getUserOverview(Long userId) {
        Map<String, Object> detail = getUserDetail(userId);
        if (detail == null) {
            return null;
        }

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", detail.get("id"));
        account.put("nickname", detail.get("nickname"));
        account.put("email", detail.get("email"));
        account.put("phoneMasked", maskPhone((String) detail.get("phone")));
        account.put("status", detail.get("status"));
        account.put("studyStage", detail.get("studyStage"));
        account.put("role", detail.get("role"));
        account.put("adminRoles", detail.get("adminRoles"));
        account.put("lastActiveAt", detail.get("lastActiveAt"));

        Map<String, Object> writing = new LinkedHashMap<>();
        Object recentEvaluations = detail.get("recentEvaluations");
        writing.put("recentEvaluations", recentEvaluations instanceof List<?> list ? list.stream().limit(3).toList() : List.of());
        writing.put("stats", detail.get("stats"));

        Map<String, Object> aiUsage = new LinkedHashMap<>();
        Map<String, Object> subscription = safeMap(detail.get("subscription"));
        aiUsage.put("todayTokens", subscription.getOrDefault("tokenUsed", 0));
        aiUsage.put("monthTokens", subscription.getOrDefault("tokenUsed", 0));
        aiUsage.put("recentFailedRequests", 0);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("account", account);
        overview.put("subscription", detail.get("subscription"));
        overview.put("writing", writing);
        overview.put("aiUsage", aiUsage);
        overview.put("audit", Map.of("recentLogs", adminUserQueryMapper.selectRecentAuditLogs(userId, 3)));
        overview.put("quickLinks", buildQuickLinks(userId));
        return overview;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public Map<String, Object> getUserDetail(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) return null;
        UserProfile profile = userProfileMapper.findByUserId(userId);
        UserAbilityProfile ability = userAbilityProfileMapper.selectByUserId(userId);
        List<EssayEvaluation> recent = essayEvaluationMapper.selectByUserId(userId, 0, 10);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("nickname", user.getNickname());
        data.put("avatarUrl", user.getAvatarUrl());
        data.put("status", user.getStatus());
        data.put("registerSource", user.getRegisterSource());
        data.put("createdAt", user.getCreatedAt());
        data.put("lastActiveAt", user.getLastActiveAt());
        data.put("role", user.getRole());
        data.put("adminRoles", adminAuthorizationService.getRoles(userId));
        data.put("studyStage", profile != null ? profile.getStudyStage() : null);
        data.put("aiMode", profile != null ? profile.getAiMode() : null);
        LocalDate today = LocalDate.now();
        data.put("subscription", adminUserQueryMapper.selectUserSubscriptionSnapshot(
                userId,
                LocalDateTime.now(),
                YearMonth.from(today).toString(),
                today
        ));

        Map<String, Object> abilityMap = new LinkedHashMap<>();
        if (ability != null) {
            abilityMap.put("taskScore", ability.getTaskScore());
            abilityMap.put("coherenceScore", ability.getCoherenceScore());
            abilityMap.put("grammarScore", ability.getGrammarScore());
            abilityMap.put("vocabularyScore", ability.getVocabularyScore());
            abilityMap.put("structureScore", ability.getStructureScore());
            abilityMap.put("varietyScore", ability.getVarietyScore());
            abilityMap.put("assessedScore", ability.getAssessedScore());
            abilityMap.put("confidence", ability.getConfidence());
            abilityMap.put("sampleCount", ability.getSampleCount());
            abilityMap.put("updatedAt", ability.getUpdatedAt());
        }
        data.put("ability", abilityMap);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEssays", essayEvaluationMapper.countByUserId(userId));
        stats.put("averageScore", essayEvaluationMapper.averageScoreByUserId(userId));
        stats.put("bestScore", essayEvaluationMapper.bestScoreByUserId(userId));
        stats.put("studyDays", essayEvaluationMapper.countDistinctDaysByUserId(userId));
        data.put("stats", stats);

        List<EvaluationHistoryResponse.Item> recentItems = recent.stream()
                .map(e -> new EvaluationHistoryResponse.Item(
                        e.getId(), e.getMode(), e.getGaokaoScore(), e.getMaxScore(), e.getBand(), e.getOverallScore(),
                        e.getEssayText(), e.getCreatedAt(), false
                ))
                .toList();
        data.put("recentEvaluations", recentItems);
        data.put("aiUsageRecords", adminUserQueryMapper.selectRecentAiUsageEvents(userId, 10));
        data.put("auditLogs", adminUserQueryMapper.selectRecentAuditLogs(userId, 10));
        data.put("quickLinks", buildQuickLinks(userId));
        return data;
    }

    private Map<String, Object> buildQuickLinks(Long userId) {
        Map<String, Object> quickLinks = new LinkedHashMap<>();
        quickLinks.put("detail", "/admin/users/" + userId);
        quickLinks.put("essays", "/admin/essays?userId=" + userId);
        quickLinks.put("subscriptions", "/admin/subscriptions?userId=" + userId);
        quickLinks.put("aiUsage", "/admin/model-usage?userId=" + userId);
        quickLinks.put("auditLogs", "/admin/audit-logs?targetUserId=" + userId);
        return quickLinks;
    }

    public void updateUserStatus(Long adminUserId, Long userId, AdminUserStatusUpdateRequest request, HttpServletRequest httpRequest) {
        User before = userMapper.findById(userId);
        userMapper.updateStatus(userId, request.getStatus());
        User after = userMapper.findById(userId);
        adminAuditService.audit(adminUserId, "UPDATE_USER_STATUS", "user", String.valueOf(userId), userId,
                before, Map.of("status", request.getStatus(), "reason", request.getReason(), "user", after), httpRequest);
    }

    public void updateUserRoles(Long adminUserId, Long userId, AdminUserRolesUpdateRequest request, HttpServletRequest httpRequest) {
        List<String> roles = adminAuthorizationService.normalizeRoles(request.getAdminRoles());
        List<String> beforeRoles = adminAuthorizationService.getRoles(userId);
        adminUserRoleMapper.deleteByUserId(userId);
        for (String role : roles) {
            adminUserRoleMapper.insert(userId, role);
        }
        userMapper.updateRole(userId, roles.isEmpty() ? "user" : "admin");
        adminAuditService.audit(adminUserId, "UPDATE_USER_ROLES", "user", String.valueOf(userId), userId,
                Map.of("adminRoles", beforeRoles), Map.of("adminRoles", roles), httpRequest);
    }
}
