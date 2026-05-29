package com.personalenglishai.backend.dto.admin;

public class AdminDataCatalogTableResponse {
    private String tableName;
    private String title;
    private String module;
    private Long rowCount;
    private String sensitivity;
    private String latestAt;
    private String adminRoute;
    private String description;
    private boolean configured;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getLatestAt() { return latestAt; }
    public void setLatestAt(String latestAt) { this.latestAt = latestAt; }
    public String getAdminRoute() { return adminRoute; }
    public void setAdminRoute(String adminRoute) { this.adminRoute = adminRoute; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }
}
