package com.personalenglishai.backend.entity.admin;

public final class AdminPermissions {
    public static final String USERS_READ = "admin.users.read";
    public static final String USERS_WRITE = "admin.users.write";
    public static final String ESSAYS_READ = "admin.essays.read";
    public static final String PROMPTS_READ = "admin.prompts.read";
    public static final String PROMPTS_WRITE = "admin.prompts.write";
    public static final String RUBRICS_READ = "admin.rubrics.read";
    public static final String RUBRICS_WRITE = "admin.rubrics.write";
    public static final String AUDIT_READ = "admin.audit.read";
    public static final String SUBSCRIPTION_READ = "admin.subscription.read";
    public static final String SUBSCRIPTION_WRITE = "admin.subscription.write";
    public static final String DATA_CATALOG_READ = "admin.data_catalog.read";
    public static final String DATA_CLEANING_READ = "admin.data_cleaning.read";
    public static final String DATA_CLEANING_WRITE = "admin.data_cleaning.write";

    private AdminPermissions() {}
}
