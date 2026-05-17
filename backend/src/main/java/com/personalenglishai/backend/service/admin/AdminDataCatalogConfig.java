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

    public AdminDataCatalogConfig() {
        this(loadFromYaml());
    }

    public AdminDataCatalogConfig(Map<String, TableConfig> tables) {
        this.tables = tables == null ? Map.of() : new LinkedHashMap<>(tables);
    }

    public TableConfig table(String tableName) {
        return tables.get(tableName);
    }

    private static Map<String, TableConfig> loadFromYaml() {
        ClassPathResource resource = new ClassPathResource("admin-data-catalog.yml");
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream input = resource.getInputStream()) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> root)) {
                return Map.of();
            }
            Map<String, TableConfig> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : root.entrySet()) {
                if (!(entry.getKey() instanceof String tableName) || !(entry.getValue() instanceof Map<?, ?> raw)) {
                    continue;
                }
                result.put(tableName, toTableConfig(raw));
            }
            return result;
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
}
