package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.entity.subscription.SubscriptionPlan;
import com.personalenglishai.backend.mapper.admin.AdminSubscriptionQueryMapper;
import com.personalenglishai.backend.mapper.subscription.SubscriptionPlanMapper;
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

class AdminSubscriptionServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-14T03:30:00Z"),
            ZoneId.of("UTC")
    );

    @Test
    void overviewUsesTodayAndCurrentMonthForUserSegments() {
        FakeAdminSubscriptionQueryMapper queryMapper = new FakeAdminSubscriptionQueryMapper();
        AdminSubscriptionService service = new AdminSubscriptionService(
                queryMapper,
                new FakeSubscriptionPlanMapper(),
                null,
                FIXED_CLOCK
        );

        Map<String, Object> overview = service.getOverview();

        assertThat(queryMapper.lastOverviewDate).isEqualTo(LocalDate.parse("2026-05-14"));
        assertThat(queryMapper.lastOverviewMonth).isEqualTo("2026-05");
        assertThat(queryMapper.planDistributionRequestedAt).isEqualTo(LocalDateTime.parse("2026-05-14T03:30"));
        assertThat(overview).containsEntry("totalUsers", 120L)
                .containsEntry("ordinaryUsers", 90L)
                .containsEntry("subscribedUsers", 30L)
                .containsEntry("todayNewUsers", 8L)
                .containsEntry("todayNewSubscriptions", 3L);
        assertThat(overview.get("planDistribution")).asList()
                .anySatisfy(row -> assertThat(row)
                        .isInstanceOf(Map.class)
                        .extracting(value -> ((Map<?, ?>) value).get("planCode"))
                        .isEqualTo("free"))
                .anySatisfy(row -> assertThat(row)
                        .isInstanceOf(Map.class)
                        .extracting(value -> ((Map<?, ?>) value).get("planCode"))
                        .isEqualTo("basic"))
                .anySatisfy(row -> assertThat(row)
                        .isInstanceOf(Map.class)
                        .extracting(value -> ((Map<?, ?>) value).get("planCode"))
                        .isEqualTo("pro"))
                .anySatisfy(row -> assertThat(row)
                        .isInstanceOf(Map.class)
                        .extracting(value -> ((Map<?, ?>) value).get("planCode"))
                        .isEqualTo("premium"));
        assertThat(overview.get("userDiagnostics")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) overview.get("userDiagnostics");
        assertThat(diagnostics)
                .containsEntry("databaseUserRows", 120L)
                .containsEntry("adminUsers", 4L)
                .containsEntry("activeUsers", 118L)
                .containsEntry("disabledUsers", 2L);
        assertThat(overview.get("adminUserPreview")).asList()
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .isInstanceOf(Map.class)
                        .extracting(value -> ((Map<?, ?>) value).get("email"))
                        .isEqualTo("admin01@admin.com"));
    }

    @Test
    void dailyStatsUseRequestedDateWindow() {
        FakeAdminSubscriptionQueryMapper queryMapper = new FakeAdminSubscriptionQueryMapper();
        AdminSubscriptionService service = new AdminSubscriptionService(
                queryMapper,
                new FakeSubscriptionPlanMapper(),
                null,
                FIXED_CLOCK
        );

        List<Map<String, Object>> rows = service.listDailyStats("2026-05-01", "2026-05-14");

        assertThat(queryMapper.lastDailyFrom).isEqualTo(LocalDate.parse("2026-05-01"));
        assertThat(queryMapper.lastDailyTo).isEqualTo(LocalDate.parse("2026-05-14"));
        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row).containsEntry("statDate", LocalDate.parse("2026-05-14"))
                        .containsEntry("newUsers", 8L)
                        .containsEntry("newSubscriptions", 3L)
        );
    }

    private static final class FakeAdminSubscriptionQueryMapper implements AdminSubscriptionQueryMapper {
        private LocalDate lastOverviewDate;
        private String lastOverviewMonth;
        private LocalDateTime planDistributionRequestedAt;
        private LocalDate lastDailyFrom;
        private LocalDate lastDailyTo;

        @Override
        public List<Map<String, Object>> searchSubscriptions(String keyword, String planCode, String subscriptionStatus,
                                                             Boolean overLimit, String expiresFrom, String expiresTo, String usageMonth,
                                                             LocalDate usageDate, LocalDateTime now, int offset, int limit) {
            return List.of();
        }

        @Override
        public long countSubscriptions(String keyword, String planCode, String subscriptionStatus,
                                       Boolean overLimit, String expiresFrom, String expiresTo, String usageMonth,
                                       LocalDate usageDate, LocalDateTime now) {
            return 0;
        }

        @Override
        public Map<String, Object> selectOverview(LocalDate today, String usageMonth, LocalDateTime now) {
            lastOverviewDate = today;
            lastOverviewMonth = usageMonth;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("totalUsers", 120L);
            map.put("ordinaryUsers", 90L);
            map.put("subscribedUsers", 30L);
            map.put("todayNewUsers", 8L);
            map.put("todayNewSubscriptions", 3L);
            map.put("todayFreeTokenUsed", 1000L);
            map.put("todayPaidTokenUsed", 2000L);
            map.put("overLimitUsers", 2L);
            map.put("sevenDaySubscriptionRate", 37.5);
            return map;
        }

        @Override
        public List<Map<String, Object>> selectPlanDistribution(LocalDateTime now) {
            planDistributionRequestedAt = now;
            return List.of(
                    planDistributionRow("free", "Free", 90L, 75.0, 0),
                    planDistributionRow("basic", "Basic", 15L, 12.5, 1),
                    planDistributionRow("pro", "Pro", 10L, 8.33, 2),
                    planDistributionRow("premium", "Premium", 5L, 4.17, 3)
            );
        }

        @Override
        public Map<String, Object> selectUserDiagnostics() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("databaseUserRows", 120L);
            map.put("activeUsers", 118L);
            map.put("disabledUsers", 2L);
            map.put("adminUsers", 4L);
            map.put("regularUsers", 116L);
            map.put("latestUserCreatedAt", LocalDateTime.parse("2026-05-14T03:20:00"));
            return map;
        }

        @Override
        public List<Map<String, Object>> selectAdminUserPreview() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", 29L);
            row.put("email", "admin01@admin.com");
            row.put("nickname", "Admin 01");
            row.put("status", "active");
            row.put("studyStage", "ielts");
            row.put("adminRolesCsv", "super_admin");
            row.put("lastActiveAt", LocalDateTime.parse("2026-05-14T03:20:00"));
            return List.of(row);
        }

        @Override
        public List<Map<String, Object>> selectDailyStats(LocalDate dateFrom, LocalDate dateTo) {
            lastDailyFrom = dateFrom;
            lastDailyTo = dateTo;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("statDate", dateTo);
            map.put("newUsers", 8L);
            map.put("newSubscriptions", 3L);
            return List.of(map);
        }

        private static Map<String, Object> planDistributionRow(String planCode,
                                                               String planName,
                                                               long userCount,
                                                               double ratio,
                                                               int sortOrder) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("planCode", planCode);
            row.put("planName", planName);
            row.put("userCount", userCount);
            row.put("ratio", ratio);
            row.put("sortOrder", sortOrder);
            return row;
        }
    }

    private static final class FakeSubscriptionPlanMapper implements SubscriptionPlanMapper {
        @Override
        public List<SubscriptionPlan> selectActivePlans() {
            return new ArrayList<>();
        }

        @Override
        public SubscriptionPlan findByPlanCode(String planCode) {
            return null;
        }

        @Override
        public int updateQuotaRule(String planCode, Long dailyTokenLimit, Long monthlyTokenLimit) {
            return 0;
        }
    }
}
