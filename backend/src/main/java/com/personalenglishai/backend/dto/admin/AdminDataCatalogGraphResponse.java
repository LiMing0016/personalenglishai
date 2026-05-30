package com.personalenglishai.backend.dto.admin;

import java.util.ArrayList;
import java.util.List;

public class AdminDataCatalogGraphResponse {
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();

    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }
    public List<Edge> getEdges() { return edges; }
    public void setEdges(List<Edge> edges) { this.edges = edges; }

    public static class Node {
        private String tableName;
        private String title;
        private String module;
        private String sensitivity;
        private Long rowCount;
        private String adminRoute;
        private boolean configured;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getSensitivity() { return sensitivity; }
        public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
        public Long getRowCount() { return rowCount; }
        public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
        public String getAdminRoute() { return adminRoute; }
        public void setAdminRoute(String adminRoute) { this.adminRoute = adminRoute; }
        public boolean isConfigured() { return configured; }
        public void setConfigured(boolean configured) { this.configured = configured; }
    }

    public static class Edge {
        private String sourceTable;
        private String sourceColumn;
        private String targetTable;
        private String targetColumn;
        private String relationType;
        private String description;

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
        public String getTargetColumn() { return targetColumn; }
        public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
