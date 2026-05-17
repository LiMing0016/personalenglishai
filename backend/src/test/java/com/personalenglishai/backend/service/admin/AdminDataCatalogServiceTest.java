package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableDetailResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableResponse;
import com.personalenglishai.backend.mapper.admin.AdminDataCatalogMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminDataCatalogServiceTest {

    @Test
    void listTablesMergesMysqlMetadataWithBusinessCatalogConfig() {
        FakeAdminDataCatalogMapper mapper = new FakeAdminDataCatalogMapper();
        AdminDataCatalogConfig config = new AdminDataCatalogConfig(Map.of(
                "users", configuredTable("用户账号", "用户中心", "high", "/admin/users", "updated_at",
                        "存储用户账号基础信息", List.of("email", "phone", "password_hash")),
                "ai_token_usage_event", configuredTable("AI Token 消耗明细", "AI 用量", "medium", "/admin/model-usage", "occurred_at",
                        "每次可统计 usage 的 AI 调用明细流水", List.of("trace_id"))
        ));
        AdminDataCatalogService service = new AdminDataCatalogService(mapper, config);

        List<AdminDataCatalogTableResponse> rows = service.listTables("token", "AI 用量", "medium", true);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTableName()).isEqualTo("ai_token_usage_event");
            assertThat(row.getTitle()).isEqualTo("AI Token 消耗明细");
            assertThat(row.getModule()).isEqualTo("AI 用量");
            assertThat(row.getRowCount()).isEqualTo(8L);
            assertThat(row.getSensitivity()).isEqualTo("medium");
            assertThat(row.getLatestAt()).isEqualTo("2026-05-16T02:52:24");
            assertThat(row.getAdminRoute()).isEqualTo("/admin/model-usage");
        });
    }

    @Test
    void getTableDetailMarksSensitiveColumnsAndKeepsRawRowsHidden() {
        FakeAdminDataCatalogMapper mapper = new FakeAdminDataCatalogMapper();
        AdminDataCatalogConfig config = new AdminDataCatalogConfig(Map.of(
                "users", configuredTable("用户账号", "用户中心", "high", "/admin/users", "updated_at",
                        "存储用户账号基础信息", List.of("email", "phone", "password_hash"))
        ));
        AdminDataCatalogService service = new AdminDataCatalogService(mapper, config);

        AdminDataCatalogTableDetailResponse detail = service.getTableDetail("users");

        assertThat(detail.getTableName()).isEqualTo("users");
        assertThat(detail.getColumns()).extracting(AdminDataCatalogTableDetailResponse.Column::getName)
                .contains("id", "email", "password_hash");
        assertThat(detail.getColumns())
                .filteredOn(AdminDataCatalogTableDetailResponse.Column::isSensitive)
                .extracting(AdminDataCatalogTableDetailResponse.Column::getName)
                .containsExactlyInAnyOrder("email", "phone", "password_hash");
        assertThat(detail.getIndexes()).extracting(AdminDataCatalogTableDetailResponse.Index::getName)
                .contains("PRIMARY", "idx_users_email");
        assertThat(detail.getForeignKeys()).isEmpty();
        assertThat(detail.getSensitiveColumns()).containsExactlyInAnyOrder("email", "phone", "password_hash");
    }

    @Test
    void getTableDetailRejectsInvalidOrUnknownTableNames() {
        AdminDataCatalogService service = new AdminDataCatalogService(
                new FakeAdminDataCatalogMapper(),
                new AdminDataCatalogConfig(Map.of())
        );

        assertThatThrownBy(() -> service.getTableDetail("users;drop table users"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("invalid table name");

        assertThatThrownBy(() -> service.getTableDetail("missing_table"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("table not found");
    }

    @Test
    void bundledCatalogConfigProvidesChineseBusinessNamesForOperationalTables() {
        AdminDataCatalogConfig config = new AdminDataCatalogConfig();

        assertThat(config.table("admin_user_role").getTitle()).isEqualTo("管理员角色关系");
        assertThat(config.table("agent_debug_run").getTitle()).isEqualTo("Agent 调试运行记录");
        assertThat(config.table("assistant_conversation").getTitle()).isEqualTo("学习助手会话");
        assertThat(config.table("document_pins").getTitle()).isEqualTo("置顶作文文档");
    }

    private static AdminDataCatalogConfig.TableConfig configuredTable(String title,
                                                                      String module,
                                                                      String sensitivity,
                                                                      String adminRoute,
                                                                      String timeColumn,
                                                                      String description,
                                                                      List<String> sensitiveColumns) {
        AdminDataCatalogConfig.TableConfig table = new AdminDataCatalogConfig.TableConfig();
        table.setTitle(title);
        table.setModule(module);
        table.setSensitivity(sensitivity);
        table.setAdminRoute(adminRoute);
        table.setTimeColumn(timeColumn);
        table.setDescription(description);
        table.setSensitiveColumns(sensitiveColumns);
        table.setSecurityNotes(List.of("不展示敏感字段原值"));
        return table;
    }

    private static final class FakeAdminDataCatalogMapper implements AdminDataCatalogMapper {
        @Override
        public List<Map<String, Object>> selectTables() {
            return List.of(
                    row("tableName", "users", "tableComment", "users", "rowCount", 120L),
                    row("tableName", "ai_token_usage_event", "tableComment", "AI token usage event ledger", "rowCount", 8L)
            );
        }

        @Override
        public List<Map<String, Object>> selectColumns(String tableName) {
            if ("users".equals(tableName)) {
                return List.of(
                        row("name", "id", "type", "bigint", "nullable", "NO", "defaultValue", null, "comment", "主键"),
                        row("name", "email", "type", "varchar(255)", "nullable", "YES", "defaultValue", null, "comment", "邮箱"),
                        row("name", "phone", "type", "varchar(32)", "nullable", "YES", "defaultValue", null, "comment", "手机号"),
                        row("name", "password_hash", "type", "varchar(255)", "nullable", "YES", "defaultValue", null, "comment", "密码 hash")
                );
            }
            if ("ai_token_usage_event".equals(tableName)) {
                return List.of(
                        row("name", "usage_event_id", "type", "varchar(96)", "nullable", "NO", "defaultValue", null, "comment", "事件 ID"),
                        row("name", "total_tokens", "type", "bigint", "nullable", "NO", "defaultValue", "0", "comment", "总 token"),
                        row("name", "occurred_at", "type", "datetime", "nullable", "NO", "defaultValue", null, "comment", "发生时间")
                );
            }
            return List.of();
        }

        @Override
        public List<Map<String, Object>> selectIndexes(String tableName) {
            if ("users".equals(tableName)) {
                return List.of(
                        row("name", "PRIMARY", "columns", "id", "uniqueIndex", true),
                        row("name", "idx_users_email", "columns", "email", "uniqueIndex", false)
                );
            }
            return List.of(row("name", "PRIMARY", "columns", "usage_event_id", "uniqueIndex", true));
        }

        @Override
        public List<Map<String, Object>> selectForeignKeys(String tableName) {
            return List.of();
        }

        @Override
        public Object selectLatestAt(String tableName, String timeColumn) {
            if ("ai_token_usage_event".equals(tableName) && "occurred_at".equals(timeColumn)) {
                return LocalDateTime.parse("2026-05-16T02:52:24");
            }
            return null;
        }

        private static Map<String, Object> row(Object... values) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i += 2) {
                row.put((String) values[i], values[i + 1]);
            }
            return row;
        }
    }
}
