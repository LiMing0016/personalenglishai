package com.personalenglishai.backend.service.auth.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSeedAccountPasswordTest {

    private static final Path ADMIN_SEED_SQL = Path.of("src/main/resources/db/seed_admin_accounts.sql");

    @Test
    @DisplayName("管理员种子账号密码应匹配文档化的 Admin123! 登录密码")
    void seededAdminPasswordHashMatchesAdminPassword() throws IOException {
        String sql = Files.readString(ADMIN_SEED_SQL);
        String hash = findSqlVariable(sql, "pwd");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertThat(encoder.matches("Admin123!", hash)).isTrue();
    }

    @Test
    @DisplayName("额外本地管理员账号密码应匹配 Kiss497.*")
    void extraLocalAdminPasswordHashMatchesConfiguredPassword() throws IOException {
        String sql = Files.readString(ADMIN_SEED_SQL);
        String hash = findSqlVariable(sql, "extra_admin_pwd");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertThat(encoder.matches("Kiss497.*", hash)).isTrue();
    }

    @Test
    @DisplayName("管理员种子脚本应支持重复执行并修复已有账号")
    void adminSeedScriptCanUpdateExistingAccounts() throws IOException {
        String sql = Files.readString(ADMIN_SEED_SQL);

        assertThat(sql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(sql).contains("password_hash = VALUES(password_hash)");
        assertThat(sql).contains("SELECT id INTO @super_id FROM users WHERE email = 'superadmin@peai.local'");
        assertThat(sql).contains("SELECT id INTO @admin01_id FROM users WHERE email = 'admin01@admin.com'");
        assertThat(sql).contains("(@admin01_id, 'super_admin', NOW(), NOW())");
    }

    private String findSqlVariable(String sql, String variableName) {
        return Pattern.compile("SET @" + Pattern.quote(variableName) + " := '([^']+)';").matcher(sql).results()
                .findFirst()
                .orElseThrow()
                .group(1);
    }
}
