package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogGraphResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableDetailResponse;
import com.personalenglishai.backend.dto.admin.AdminDataCatalogTableResponse;
import com.personalenglishai.backend.mapper.admin.AdminDataCatalogMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminDataCatalogService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final List<String> INFERRED_TIME_COLUMNS = List.of(
            "updated_at",
            "occurred_at",
            "last_lookup_at",
            "finished_at",
            "started_at",
            "created_at",
            "favorited_at"
    );

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
        assertIdentifier(tableName);
        Map<String, Object> table = mapper.selectTables().stream()
                .filter(row -> tableName.equals(stringValue(row.get("tableName"))))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "table not found"));

        List<Map<String, Object>> rawColumns = mapper.selectColumns(tableName);
        Set<String> columnNames = rawColumns.stream()
                .map(row -> stringValue(row.get("name")))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        AdminDataCatalogTableDetailResponse detail = new AdminDataCatalogTableDetailResponse();
        fillTableFields(detail, table);
        AdminDataCatalogConfig.TableConfig tableConfig = config.table(tableName);
        Set<String> sensitiveColumns = new LinkedHashSet<>(tableConfig == null ? List.of() : tableConfig.getSensitiveColumns());
        detail.setColumns(rawColumns.stream().map(row -> toColumn(row, sensitiveColumns, primaryKeys(tableName))).toList());
        detail.setIndexes(mapper.selectIndexes(tableName).stream().map(this::toIndex).toList());
        detail.setForeignKeys(mapper.selectForeignKeys(tableName).stream().map(this::toForeignKey).toList());
        detail.setRelationships(buildRelationshipsForTable(tableName));
        detail.setSensitiveColumns(sensitiveColumns.stream().filter(columnNames::contains).toList());
        detail.setSecurityNotes(tableConfig == null ? List.of() : tableConfig.getSecurityNotes());
        return detail;
    }

    public AdminDataCatalogGraphResponse getGraph(String module, String tableName) {
        if (!isBlank(tableName)) {
            assertIdentifier(tableName);
        }

        Map<String, AdminDataCatalogTableResponse> tableMap = mapper.selectTables().stream()
                .map(this::toTableResponse)
                .collect(Collectors.toMap(
                        AdminDataCatalogTableResponse::getTableName,
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (!isBlank(tableName) && !tableMap.containsKey(tableName)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "table not found");
        }

        List<GraphEdgeRecord> allEdges = allGraphEdges(tableMap.keySet());
        Set<String> selectedTables = selectTablesForGraph(tableMap, allEdges, module, tableName);

        AdminDataCatalogGraphResponse response = new AdminDataCatalogGraphResponse();
        response.setNodes(selectedTables.stream()
                .map(tableMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AdminDataCatalogTableResponse::getTableName))
                .map(this::toGraphNode)
                .toList());
        response.setEdges(allEdges.stream()
                .filter(edge -> selectedTables.contains(edge.sourceTable()) && selectedTables.contains(edge.targetTable()))
                .sorted(Comparator.comparing(GraphEdgeRecord::sourceTable)
                        .thenComparing(GraphEdgeRecord::targetTable)
                        .thenComparing(GraphEdgeRecord::sourceColumn, Comparator.nullsLast(String::compareTo)))
                .map(this::toGraphEdge)
                .toList());
        return response;
    }

    public String exportMermaid(String module, String tableName) {
        AdminDataCatalogGraphResponse graph = getGraph(module, tableName);
        StringBuilder builder = new StringBuilder("flowchart LR\n");
        for (AdminDataCatalogGraphResponse.Node node : graph.getNodes()) {
            builder.append("  ")
                    .append(mermaidNodeId(node.getTableName()))
                    .append("[\"")
                    .append(escapeMermaidLabel(firstNonBlank(node.getTitle(), node.getTableName())))
                    .append("<br/><span style='font-size:11px'>")
                    .append(escapeMermaidLabel(node.getTableName()))
                    .append("</span>\"]\n");
        }
        for (AdminDataCatalogGraphResponse.Edge edge : graph.getEdges()) {
            builder.append("  ")
                    .append(mermaidNodeId(edge.getSourceTable()))
                    .append(" ")
                    .append("logical".equalsIgnoreCase(edge.getRelationType()) ? "-.->" : "-->")
                    .append("|\"")
                    .append(escapeMermaidLabel(edgeLabel(edge)))
                    .append("\"| ")
                    .append(mermaidNodeId(edge.getTargetTable()))
                    .append("\n");
        }
        return builder.toString();
    }

    public String exportDbml(String module, String tableName) {
        AdminDataCatalogGraphResponse graph = getGraph(module, tableName);
        Set<String> selectedTables = graph.getNodes().stream()
                .map(AdminDataCatalogGraphResponse.Node::getTableName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder builder = new StringBuilder();
        for (String selectedTable : selectedTables) {
            builder.append("Table ").append(selectedTable).append(" {\n");
            List<Map<String, Object>> columns = mapper.selectColumns(selectedTable);
            Set<String> primaryKeys = primaryKeys(selectedTable);
            for (Map<String, Object> column : columns) {
                String columnName = stringValue(column.get("name"));
                String columnType = firstNonBlank(stringValue(column.get("type")), "varchar");
                builder.append("  ").append(columnName).append(" ").append(columnType);
                List<String> tags = new ArrayList<>();
                if (primaryKeys.contains(columnName)) {
                    tags.add("pk");
                }
                if (!booleanValue(column.get("nullable"))) {
                    tags.add("not null");
                }
                if (!tags.isEmpty()) {
                    builder.append(" [").append(String.join(", ", tags)).append("]");
                }
                String comment = stringValue(column.get("comment"));
                if (!isBlank(comment)) {
                    builder.append(" // ").append(comment);
                }
                builder.append("\n");
            }
            builder.append("}\n\n");
        }

        for (AdminDataCatalogGraphResponse.Edge edge : graph.getEdges()) {
            if (isBlank(edge.getSourceColumn()) || isBlank(edge.getTargetColumn())) {
                continue;
            }
            builder.append("Ref: ")
                    .append(edge.getSourceTable()).append(".").append(edge.getSourceColumn())
                    .append(" > ")
                    .append(edge.getTargetTable()).append(".").append(edge.getTargetColumn());
            if ("logical".equalsIgnoreCase(edge.getRelationType())) {
                builder.append(" // logical");
            }
            builder.append("\n");
        }
        return builder.toString();
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
        response.setConfigured(tableConfig != null);
        response.setLatestAt(resolveLatestAt(tableName, tableConfig));
    }

    private String resolveLatestAt(String tableName, AdminDataCatalogConfig.TableConfig tableConfig) {
        List<String> availableColumns = mapper.selectColumns(tableName).stream()
                .map(row -> stringValue(row.get("name")))
                .filter(Objects::nonNull)
                .toList();

        String timeColumn = tableConfig == null ? null : tableConfig.getTimeColumn();
        if (!isIdentifier(timeColumn) || !availableColumns.contains(timeColumn)) {
            timeColumn = INFERRED_TIME_COLUMNS.stream()
                    .filter(availableColumns::contains)
                    .findFirst()
                    .orElse(null);
        }
        if (!isIdentifier(timeColumn)) {
            return null;
        }
        Object value = mapper.selectLatestAt(tableName, timeColumn);
        return value == null ? null : String.valueOf(value);
    }

    private Set<String> primaryKeys(String tableName) {
        return mapper.selectIndexes(tableName).stream()
                .filter(row -> "PRIMARY".equalsIgnoreCase(stringValue(row.get("name"))))
                .flatMap(row -> List.of(firstNonBlank(stringValue(row.get("columns")), "")).stream())
                .flatMap(columns -> List.of(columns.split(",")).stream())
                .map(String::trim)
                .filter(column -> !column.isBlank())
                .collect(Collectors.toSet());
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

    private List<AdminDataCatalogTableDetailResponse.Relationship> buildRelationshipsForTable(String tableName) {
        Set<String> knownTables = mapper.selectTables().stream()
                .map(row -> stringValue(row.get("tableName")))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return allGraphEdges(knownTables).stream()
                .filter(edge -> tableName.equals(edge.sourceTable()) || tableName.equals(edge.targetTable()))
                .map(edge -> {
                    AdminDataCatalogTableDetailResponse.Relationship relationship = new AdminDataCatalogTableDetailResponse.Relationship();
                    relationship.setSourceTable(edge.sourceTable());
                    relationship.setSourceColumn(edge.sourceColumn());
                    relationship.setTargetTable(edge.targetTable());
                    relationship.setTargetColumn(edge.targetColumn());
                    relationship.setRelationType(edge.relationType());
                    relationship.setDirection(tableName.equals(edge.sourceTable()) ? "outgoing" : "incoming");
                    relationship.setDescription(firstNonBlank(edge.description(), edgeLabel(toGraphEdge(edge))));
                    return relationship;
                })
                .sorted(Comparator.comparing(AdminDataCatalogTableDetailResponse.Relationship::getDirection)
                        .thenComparing(AdminDataCatalogTableDetailResponse.Relationship::getSourceTable)
                        .thenComparing(AdminDataCatalogTableDetailResponse.Relationship::getTargetTable))
                .toList();
    }

    private AdminDataCatalogGraphResponse.Node toGraphNode(AdminDataCatalogTableResponse row) {
        AdminDataCatalogGraphResponse.Node node = new AdminDataCatalogGraphResponse.Node();
        node.setTableName(row.getTableName());
        node.setTitle(row.getTitle());
        node.setModule(row.getModule());
        node.setSensitivity(row.getSensitivity());
        node.setRowCount(row.getRowCount());
        node.setAdminRoute(row.getAdminRoute());
        node.setConfigured(row.isConfigured());
        return node;
    }

    private AdminDataCatalogGraphResponse.Edge toGraphEdge(GraphEdgeRecord edge) {
        AdminDataCatalogGraphResponse.Edge response = new AdminDataCatalogGraphResponse.Edge();
        response.setSourceTable(edge.sourceTable());
        response.setSourceColumn(edge.sourceColumn());
        response.setTargetTable(edge.targetTable());
        response.setTargetColumn(edge.targetColumn());
        response.setRelationType(edge.relationType());
        response.setDescription(edge.description());
        return response;
    }

    private Set<String> selectTablesForGraph(Map<String, AdminDataCatalogTableResponse> tableMap,
                                             List<GraphEdgeRecord> allEdges,
                                             String module,
                                             String tableName) {
        if (!isBlank(tableName)) {
            Set<String> selected = new LinkedHashSet<>();
            selected.add(tableName);
            allEdges.stream()
                    .filter(edge -> tableName.equals(edge.sourceTable()) || tableName.equals(edge.targetTable()))
                    .forEach(edge -> {
                        selected.add(edge.sourceTable());
                        selected.add(edge.targetTable());
                    });
            return selected;
        }

        return tableMap.values().stream()
                .filter(row -> isBlank(module) || module.equals(row.getModule()))
                .map(AdminDataCatalogTableResponse::getTableName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<GraphEdgeRecord> allGraphEdges(Set<String> knownTables) {
        List<GraphEdgeRecord> edges = new ArrayList<>();
        mapper.selectAllForeignKeys().forEach(row -> {
            String sourceTable = stringValue(row.get("tableName"));
            String targetTable = stringValue(row.get("referencedTableName"));
            if (!knownTables.contains(sourceTable) || !knownTables.contains(targetTable)) {
                return;
            }
            edges.add(new GraphEdgeRecord(
                    sourceTable,
                    stringValue(row.get("columnName")),
                    targetTable,
                    stringValue(row.get("referencedColumnName")),
                    "physical",
                    stringValue(row.get("name"))
            ));
        });
        for (AdminDataCatalogConfig.LogicalRelationConfig relation : config.logicalRelations()) {
            if (!knownTables.contains(relation.getSourceTable()) || !knownTables.contains(relation.getTargetTable())) {
                continue;
            }
            edges.add(new GraphEdgeRecord(
                    relation.getSourceTable(),
                    relation.getSourceColumn(),
                    relation.getTargetTable(),
                    relation.getTargetColumn(),
                    "logical",
                    relation.getDescription()
            ));
        }
        return deduplicateEdges(edges);
    }

    private List<GraphEdgeRecord> deduplicateEdges(List<GraphEdgeRecord> edges) {
        Map<String, GraphEdgeRecord> unique = new LinkedHashMap<>();
        for (GraphEdgeRecord edge : edges) {
            String key = String.join("|",
                    firstNonBlank(edge.sourceTable(), ""),
                    firstNonBlank(edge.sourceColumn(), ""),
                    firstNonBlank(edge.targetTable(), ""),
                    firstNonBlank(edge.targetColumn(), ""),
                    firstNonBlank(edge.relationType(), ""));
            unique.putIfAbsent(key, edge);
        }
        return new ArrayList<>(unique.values());
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

    private void assertIdentifier(String value) {
        if (!isIdentifier(value)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "invalid table name");
        }
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

    private static String mermaidNodeId(String tableName) {
        return "tbl_" + tableName.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String escapeMermaidLabel(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "\\\"");
    }

    private static String edgeLabel(AdminDataCatalogGraphResponse.Edge edge) {
        String source = firstNonBlank(edge.getSourceColumn(), "?");
        String target = firstNonBlank(edge.getTargetColumn(), "?");
        if ("logical".equalsIgnoreCase(edge.getRelationType())) {
            return "[logical] " + source + " -> " + target;
        }
        return source + " -> " + target;
    }

    private record GraphEdgeRecord(String sourceTable,
                                   String sourceColumn,
                                   String targetTable,
                                   String targetColumn,
                                   String relationType,
                                   String description) {
    }
}
