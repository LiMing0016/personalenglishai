package com.personalenglishai.backend.dto.admin;

import java.util.List;

public class AdminUserRolesUpdateRequest {
    private List<String> adminRoles;

    public List<String> getAdminRoles() { return adminRoles; }
    public void setAdminRoles(List<String> adminRoles) { this.adminRoles = adminRoles; }
}
