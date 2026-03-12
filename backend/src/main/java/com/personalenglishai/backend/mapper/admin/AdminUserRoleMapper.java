package com.personalenglishai.backend.mapper.admin;

import com.personalenglishai.backend.entity.admin.AdminUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserRoleMapper {
    List<AdminUserRole> selectByUserId(@Param("userId") Long userId);
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);
    int deleteByUserId(@Param("userId") Long userId);
    int insert(@Param("userId") Long userId, @Param("role") String role);
    long countByUserId(@Param("userId") Long userId);
}
