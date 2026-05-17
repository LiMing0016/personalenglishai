package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableDetailResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableResponse;
import com.personalenglishai.backend.mapper.admin.AdminDataCatalogMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AdminDataCatalogService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private final AdminDataCatalogMapper mapper;
    private final AdminDataCatalogConfig config;

    public AdminDataCatalogService(AdminDataCatalogMapper mapper, AdminDataCatalogConfig config) {
        this.mapper = mapper;
        this.config = config;
    }

    public List<AdminDataCatalogTableResponse> listTables(String keyword,
                                                          String module,
                                                          String sensitivity,
                                                          Boolean hasAdminRoute) {
        return mapper.selectTables().stream()
                .map(this::toTableResponse)
                .filter(row -> matches(row, keyword, module, sensitivity, hasAdminRoute))
                .toList();
    }

    public AdminDataCatalogTableDetailResponse getTableDetail(String tableName) {
        if (!isIdentifier(tableName)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "invalid table name");
        }
        Map<String, Object> table = mapper.selectTables().stream()
                .filter(row -> tableName.equals(stringValue(row.get("tableName"))))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "table not found"));

        List<Map<String, Object>> rawColumns = mapper.selectColumns(tableName);
        Set<String> columnNames = rawColumns.stream()
                .map(row -> stringValue(row.get("name")))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        AdminDataCatalogTableDetailResponse detail = new AdminDataCatalogTableDetailResponse();
        fillTableFields(detail, table);
        AdminDataCatalogConfig.TableConfig tableConfig = config.table(tableName);
        Set<String> sensitiveColumns = new LinkedHashSet<>(tableConfig == null ? List.of() : tableConfig.getSensitiveColumns());
        detail.setColumns(rawColumns.stream().map(row -> toColumn(row, sensitiveColumns, primaryKeys(tableName))).toList());
        detail.setIndexes(mapper.selectIndexes(tableName).stream().map(this::toIndex).toList());
        detail.setForeignKeys(mapper.selectForeignKeys(tableName).stream().map(this::toForeignKey).toList());
        detail.setSensitiveColumns(sensitiveColumns.stream().filter(columnNames::contains).toList());
        detail.setSecurityNotes(tableConfig == null ? List.of() : tableConfig.getSecurityNotes());
        return detail;
    }

    private AdminDataCatalogTableResponse toTableResponse(Map<String, Object> table) {
        AdminDataCatalogTableResponse response = new AdminDataCatalogTableResponse();
        fillTableFields(response, table);
        return response;
    }

    private void fillTableFields(AdminDataCatalogTableResponse response, Map<String, Object> table) {
        String tableName = stringValue(table.get("tableName"));
        AdminDataCatalogConfig.TableConfig tableConfig = config.table(tableName);
        response.setTableName(tableName);
        response.setTitle(firstNonBlank(tableConfig == null ? null : tableConfig.getTitle(), tableName));
        response.setModule(tableConfig == null ? null : tableConfig.getModule());
        response.setRowCount(longValue(table.get("rowCount")));
        response.setSensitivity(firstNonBlank(tableConfig == null ? null : tableConfig.getSensitivity(), "low"));
        response.setAdminRoute(tableConfig == null ? null : tableConfig.getAdminRoute());
        response.setDescription(tableConfig == null ? null : tableConfig.getDescription());
        response.setLatestAt(resolveLatestAt(tableName, tableConfig));
    }

    private String resolveLatestAt(String tableName, AdminDataCatalogConfig.TableConfig tableConfig) {
        if (tableConfig == null || !isIdentifier(tableConfig.getTimeColumn())) {
            return null;
        }
        boolean columnExists = mapper.selectColumns(tableName).stream()
                .anyMatch(row -> tableConfig.getTimeColumn().equals(stringValue(row.get("name"))));
        if (!columnExists) {
            return null;
        }
        Object value = mapper.selectLatestAt(tableName, tableConfig.getTimeColumn());
        return value == null ? null : String.valueOf(value);
    }

    private Set<String> primaryKeys(String tableName) {
        return mapper.selectIndexes(tableName).stream()
                .filter(row -> "PRIMARY".equalsIgnoreCase(stringValue(row.get("name"))))
                .flatMap(row -> List.of(firstNonBlank(stringValue(row.get("columns")), "")).stream())
                .flatMap(columns -> List.of(columns.split(",")).stream())
                .map(String::trim)
                .filter(column -> !column.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private AdminDataCatalogTableDetailResponse.Column toColumn(Map<String, Object> row, Set<String> sensitiveColumns, Set<String> primaryKeys) {
        AdminDataCatalogTableDetailResponse.Column column = new AdminDataCatalogTableDetailResponse.Column();
        String name = stringValue(row.get("name"));
        column.setName(name);
        column.setType(stringValue(row.get("type")));
        column.setNullable(booleanValue(row.get("nullable")));
        column.setDefaultValue(stringValue(row.get("defaultValue")));
        column.setPrimaryKey(primaryKeys.contains(name));
        column.setSensitive(sensitiveColumns.contains(name));
        column.setComment(stringValue(row.get("comment")));
        return column;
    }

    private AdminDataCatalogTableDetailResponse.Index toIndex(Map<String, Object> row) {
        AdminDataCatalogTableDetailResponse.Index index = new AdminDataCatalogTableDetailResponse.Index();
        index.setName(stringValue(row.get("name")));
        index.setColumns(stringValue(row.get("columns")));
        index.setUniqueIndex(booleanValue(row.get("uniqueIndex")));
        return index;
    }

    private AdminDataCatalogTableDetailResponse.ForeignKey toForeignKey(Map<String, Object> row) {
        AdminDataCatalogTableDetailResponse.ForeignKey foreignKey = new AdminDataCatalogTableDetailResponse.ForeignKey();
        foreignKey.setName(stringValue(row.get("name")));
        foreignKey.setColumnName(stringValue(row.get("columnName")));
        foreignKey.setReferencedTableName(stringValue(row.get("referencedTableName")));
        foreignKey.setReferencedColumnName(stringValue(row.get("referencedColumnName")));
        return foreignKey;
    }

    private boolean matches(AdminDataCatalogTableResponse row,
                            String keyword,
                            String module,
                            String sensitivity,
                            Boolean hasAdminRoute) {
        if (!isBlank(keyword)) {
            String haystack = (row.getTableName() + " " + row.getTitle() + " " + row.getDescription()).toLowerCase(Locale.ROOT);
            if (!haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!isBlank(module) && !module.equals(row.getModule())) {
            return false;
        }
        if (!isBlank(sensitivity) && !sensitivity.equals(row.getSensitivity())) {
            return false;
        }
        if (hasAdminRoute != null) {
            boolean hasRoute = !isBlank(row.getAdminRoute());
            return hasAdminRoute == hasRoute;
        }
        return true;
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value);
        return "YES".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
    }
}
