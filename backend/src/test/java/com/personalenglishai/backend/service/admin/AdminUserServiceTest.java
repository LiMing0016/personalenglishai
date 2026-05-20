package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.mapper.admin.AdminUserQueryMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserServiceTest {

    @Test
    void listUsersIncludesSubscriptionQuotaAndPassesUserAssetFilters() {
        FakeAdminUserQueryMapper mapper = new FakeAdminUserQueryMapper();
        AdminUserService service = new AdminUserService(
                null,
                null,
                mapper,
                null,
                null,
                null,
                null,
                null
        );

        AdminPageResponse<Map<String, Object>> page = service.listUsers(
                31L,
                "admin",
                "active",
                "admin",
                "email",
                "super_admin",
                "ielts",
                "2026-05-01 00:00:00",
                "2026-05-15 23:59:59",
                "2026-05-01 00:00:00",
                "2026-05-15 23:59:59",
                "pro",
                "active",
                true,
                1,
                20
        );

        assertThat(mapper.userId).isEqualTo(31L);
        assertThat(mapper.keyword).isEqualTo("admin");
        assertThat(mapper.role).isEqualTo("admin");
        assertThat(mapper.adminRole).isEqualTo("super_admin");
        assertThat(mapper.studyStage).isEqualTo("ielts");
        assertThat(mapper.planCode).isEqualTo("pro");
        assertThat(mapper.subscriptionStatus).isEqualTo("active");
        assertThat(mapper.overLimit).isTrue();
        assertThat(mapper.createdFrom).isEqualTo("2026-05-01 00:00:00");
        assertThat(mapper.createdTo).isEqualTo("2026-05-15 23:59:59");
        assertThat(mapper.now).isNotNull();
        assertThat(mapper.usageMonth).isNotBlank();
        assertThat(mapper.usageDate).isNotNull();

        assertThat(page.getTotal()).isEqualTo(1);
        Map<String, Object> item = page.getItems().get(0);
        assertThat(item).containsEntry("planCode", "pro")
                .containsEntry("subscriptionStatus", "active")
                .containsEntry("quotaPeriod", "monthly")
                .containsEntry("tokenLimit", 1000000L)
                .containsEntry("tokenUsed", 120000L)
                .containsEntry("tokenRemaining", 880000L)
                .containsEntry("overLimit", false);
        assertThat(item.get("adminRoles")).isEqualTo(List.of("super_admin", "support_admin"));
        assertThat(item).doesNotContainKey("adminRolesCsv");
    }

    private static class FakeAdminUserQueryMapper implements AdminUserQueryMapper {
        String keyword;
        String role;
        String adminRole;
        String studyStage;
        String createdFrom;
        String createdTo;
        String planCode;
        String subscriptionStatus;
        Boolean overLimit;
        LocalDateTime now;
        String usageMonth;
        LocalDate usageDate;
        Long userId;

        @Override
        public List<Map<String, Object>> searchUsers(Long userId, String keyword, String status, String role, String registerSource,
                                                     String adminRole, String studyStage, String lastActiveFrom,
                                                     String lastActiveTo, String createdFrom, String createdTo,
                                                     String planCode, String subscriptionStatus, Boolean overLimit,
                                                     LocalDateTime now, String usageMonth, LocalDate usageDate,
                                                     int offset, int limit) {
            this.userId = userId;
            this.keyword = keyword;
            this.role = role;
            this.adminRole = adminRole;
            this.studyStage = studyStage;
            this.createdFrom = createdFrom;
            this.createdTo = createdTo;
            this.planCode = planCode;
            this.subscriptionStatus = subscriptionStatus;
            this.overLimit = overLimit;
            this.now = now;
            this.usageMonth = usageMonth;
            this.usageDate = usageDate;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 31L);
            row.put("email", "admin03@admin.com");
            row.put("nickname", "Admin 03");
            row.put("status", "active");
            row.put("role", "admin");
            row.put("studyStage", "ielts");
            row.put("planCode", "pro");
            row.put("planName", "Pro");
            row.put("subscriptionStatus", "active");
            row.put("quotaPeriod", "monthly");
            row.put("tokenLimit", 1000000L);
            row.put("tokenUsed", 120000L);
            row.put("tokenRemaining", 880000L);
            row.put("overLimit", false);
            row.put("adminRolesCsv", "super_admin,support_admin");
            return List.of(row);
        }

        @Override
        public long countUsers(Long userId, String keyword, String status, String role, String registerSource, String adminRole,
                               String studyStage, String lastActiveFrom, String lastActiveTo, String createdFrom,
                               String createdTo, String planCode, String subscriptionStatus, Boolean overLimit,
                               LocalDateTime now, String usageMonth, LocalDate usageDate) {
            return 1;
        }

        @Override
        public Map<String, Object> selectUserSubscriptionSnapshot(Long userId, LocalDateTime now, String usageMonth,
                                                                  LocalDate usageDate) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> selectRecentAiUsageEvents(Long userId, int limit) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> selectRecentAuditLogs(Long userId, int limit) {
            return List.of();
        }
    }
}
