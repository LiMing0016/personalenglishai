package com.personalenglishai.backend.entity.admin;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AdminRoles {
    public static final String SUPER_ADMIN = "super_admin";
    public static final String SUPPORT_ADMIN = "support_admin";
    public static final String CONTENT_ADMIN = "content_admin";

    public static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            SUPER_ADMIN, Set.of(
                    AdminPermissions.USERS_READ,
                    AdminPermissions.USERS_WRITE,
                    AdminPermissions.ESSAYS_READ,
                    AdminPermissions.PROMPTS_READ,
                    AdminPermissions.PROMPTS_WRITE,
                    AdminPermissions.RUBRICS_READ,
                    AdminPermissions.RUBRICS_WRITE,
                    AdminPermissions.AUDIT_READ
            ),
            SUPPORT_ADMIN, Set.of(
                    AdminPermissions.USERS_READ,
                    AdminPermissions.USERS_WRITE,
                    AdminPermissions.ESSAYS_READ
            ),
            CONTENT_ADMIN, Set.of(
                    AdminPermissions.PROMPTS_READ,
                    AdminPermissions.PROMPTS_WRITE,
                    AdminPermissions.RUBRICS_READ,
                    AdminPermissions.RUBRICS_WRITE
            )
    );

    private AdminRoles() {}

    public static boolean isSupported(String role) {
        return ROLE_PERMISSIONS.containsKey(role);
    }

    public static Set<String> permissionsOf(Iterable<String> roles) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String role : roles) {
            Set<String> permissions = ROLE_PERMISSIONS.get(role);
            if (permissions != null) out.addAll(permissions);
        }
        return out;
    }
}
