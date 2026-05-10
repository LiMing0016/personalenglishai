package com.personalenglishai.backend.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSchemaTest {

    @Test
    void schemaContainsEmailVerificationTokenDefinition() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of("src/main/resources/db/create_email_verification.sql"));

        assertAll(
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS email_verification_token")),
                () -> assertTrue(schema.contains("token      VARCHAR(128) NOT NULL UNIQUE")),
                () -> assertTrue(schema.contains("expires_at DATETIME NOT NULL")),
                () -> assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS email_verification_token")),
                () -> assertTrue(migration.contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified"))
        );
    }
}
