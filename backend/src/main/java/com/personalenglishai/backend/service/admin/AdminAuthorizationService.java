package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.entity.admin.AdminRoles;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.mapper.admin.AdminUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminAuthorizationService {
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final UserMapper userMapper;

    public AdminAuthorizationService(AdminUserRoleMapper adminUserRoleMapper, UserMapper userMapper) {
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.userMapper = userMapper;
    }

    public List<String> getRoles(Long userId) {
        if (userId == null) return List.of();
        return adminUserRoleMapper.selectRoleNamesByUserId(userId);
    }

    public Set<String> getPermissions(Long userId) {
        return new LinkedHashSet<>(AdminRoles.permissionsOf(getRoles(userId)));
    }

    public User requireAdmin(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || getRoles(userId).isEmpty()) {
            throw new BizException(ErrorCode.ADMIN_FORBIDDEN);
        }
        return user;
    }

    public void requirePermission(Long userId, String permission) {
        requireAdmin(userId);
        if (!getPermissions(userId).contains(permission)) {
            throw new BizException(ErrorCode.ADMIN_FORBIDDEN);
        }
    }

    public void requireRole(Long userId, String role) {
        requireAdmin(userId);
        if (!getRoles(userId).contains(role)) {
            throw new BizException(ErrorCode.ADMIN_FORBIDDEN);
        }
    }

    public void validateRoles(List<String> roles) {
        if (roles == null) return;
        for (String role : roles) {
            if (!AdminRoles.isSupported(role)) {
                throw new BizException(ErrorCode.ADMIN_ROLE_INVALID, "invalid role: " + role);
            }
        }
    }

    public List<String> normalizeRoles(List<String> roles) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) set.add(role.trim());
            }
        }
        validateRoles(new ArrayList<>(set));
        return new ArrayList<>(set);
    }
}
