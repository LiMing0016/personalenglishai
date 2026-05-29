package com.personalenglishai.backend.service.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminDataCatalogConfig {
    private final Map<String, TableConfig> tables;
    private final List<LogicalRelationConfig> logicalRelations;

    public AdminDataCatalogConfig() {
        this(loadFromYamlInternal());
    }

    public AdminDataCatalogConfig(Map<String, TableConfig> tables) {
        this(tables, List.of());
    }

    public AdminDataCatalogConfig(Map<String, TableConfig> tables, List<LogicalRelationConfig> logicalRelations) {
        this.tables = tables == null ? Map.of() : new LinkedHashMap<>(tables);
        this.logicalRelations = logicalRelations == null ? List.of() : List.copyOf(logicalRelations);
    }

    private AdminDataCatalogConfig(LoadedConfig loadedConfig) {
        this(loadedConfig.tables(), loadedConfig.logicalRelations());
    }

    public TableConfig table(String tableName) {
        return tables.get(tableName);
    }

    public List<LogicalRelationConfig> logicalRelations() {
        return logicalRelations;
    }

    private static LoadedConfig loadFromYamlInternal() {
        ClassPathResource resource = new ClassPathResource("admin-data-catalog.yml");
        if (!resource.exists()) {
            return new LoadedConfig(Map.of(), List.of());
        }
        try (InputStream input = resource.getInputStream()) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> root)) {
                return new LoadedConfig(Map.of(), List.of());
            }
            Map<String, TableConfig> result = new LinkedHashMap<>();
            List<LogicalRelationConfig> logicalRelations = new ArrayList<>();
            for (Map.Entry<?, ?> entry : root.entrySet()) {
                if (!(entry.getKey() instanceof String tableName) || !(entry.getValue() instanceof Map<?, ?> raw)) {
                    if ("_logicalRelations".equals(entry.getKey())) {
                        logicalRelations = logicalRelationList(entry.getValue());
                    }
                    continue;
                }
                if (tableName.startsWith("_")) {
                    if ("_logicalRelations".equals(tableName)) {
                        logicalRelations = logicalRelationList(entry.getValue());
                    }
                    continue;
                }
                result.put(tableName, toTableConfig(raw));
            }
            return new LoadedConfig(result, logicalRelations);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load admin-data-catalog.yml", e);
        }
    }

    private static TableConfig toTableConfig(Map<?, ?> raw) {
        TableConfig config = new TableConfig();
        config.setTitle(stringValue(raw.get("title")));
        config.setModule(stringValue(raw.get("module")));
        config.setSensitivity(stringValue(raw.get("sensitivity")));
        config.setAdminRoute(stringValue(raw.get("adminRoute")));
        config.setTimeColumn(stringValue(raw.get("timeColumn")));
        config.setDescription(stringValue(raw.get("description")));
        config.setSensitiveColumns(stringList(raw.get("sensitiveColumns")));
        config.setSecurityNotes(stringList(raw.get("securityNotes")));
        return config;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static List<LogicalRelationConfig> logicalRelationList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<LogicalRelationConfig> relations = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> relationMap)) {
                continue;
            }
            LogicalRelationConfig relation = new LogicalRelationConfig();
            relation.setSourceTable(stringValue(relationMap.get("sourceTable")));
            relation.setSourceColumn(stringValue(relationMap.get("sourceColumn")));
            relation.setTargetTable(stringValue(relationMap.get("targetTable")));
            relation.setTargetColumn(stringValue(relationMap.get("targetColumn")));
            relation.setDescription(stringValue(relationMap.get("description")));
            if (relation.isValid()) {
                relations.add(relation);
            }
        }
        return relations;
    }

    public static class TableConfig {
        private String title;
        private String module;
        private String sensitivity;
        private String adminRoute;
        private String timeColumn;
        private String description;
        private List<String> sensitiveColumns = new ArrayList<>();
        private List<String> securityNotes = new ArrayList<>();

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getSensitivity() { return sensitivity; }
        public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
        public String getAdminRoute() { return adminRoute; }
        public void setAdminRoute(String adminRoute) { this.adminRoute = adminRoute; }
        public String getTimeColumn() { return timeColumn; }
        public void setTimeColumn(String timeColumn) { this.timeColumn = timeColumn; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getSensitiveColumns() { return sensitiveColumns; }
        public void setSensitiveColumns(List<String> sensitiveColumns) { this.sensitiveColumns = sensitiveColumns == null ? List.of() : sensitiveColumns; }
        public List<String> getSecurityNotes() { return securityNotes; }
        public void setSecurityNotes(List<String> securityNotes) { this.securityNotes = securityNotes == null ? List.of() : securityNotes; }
    }

    public static class LogicalRelationConfig {
        private String sourceTable;
        private String sourceColumn;
        private String targetTable;
        private String targetColumn;
        private String description;

        public boolean isValid() {
            return notBlank(sourceTable) && notBlank(targetTable);
        }

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
        public String getTargetColumn() { return targetColumn; }
        public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    private record LoadedConfig(Map<String, TableConfig> tables, List<LogicalRelationConfig> logicalRelations) {}

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
