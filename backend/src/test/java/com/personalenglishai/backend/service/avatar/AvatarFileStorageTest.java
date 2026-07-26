package com.personalenglishai.backend.service.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesRandomPngInsideCurrentUserDirectory() {
        AvatarFileStorage storage = storage();
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};

        AvatarFileStorage.StoredAvatar first = storage.store(42L, png);
        AvatarFileStorage.StoredAvatar second = storage.store(42L, png);

        Path expectedDirectory = tempDir.resolve("avatars").resolve("42").toAbsolutePath().normalize();
        assertThat(first.path().getParent()).isEqualTo(expectedDirectory);
        assertThat(first.path()).hasExtension("png");
        assertThat(first.path().getFileName().toString()).doesNotContain("profile");
        assertThat(first.avatarUrl()).startsWith("/uploads/avatars/42/").endsWith(".png");
        assertThat(first.path()).exists().hasBinaryContent(png);
        assertThat(second.path()).isNotEqualTo(first.path());
    }

    @Test
    void deleteNewFileRemovesStoredFile() {
        AvatarFileStorage storage = storage();
        AvatarFileStorage.StoredAvatar avatar = storage.store(42L, new byte[] {1, 2, 3});

        storage.deleteNewFile(avatar);

        assertThat(avatar.path()).doesNotExist();
    }

    @Test
    void deletesPreviousAvatarOnlyWhenOwnedByCurrentUser() throws IOException {
        AvatarFileStorage storage = storage();
        AvatarFileStorage.StoredAvatar previous = storage.store(42L, new byte[] {1});
        AvatarFileStorage.StoredAvatar replacement = storage.store(42L, new byte[] {2});

        storage.deletePreviousIfOwned(42L, previous.avatarUrl(), replacement);

        assertThat(previous.path()).doesNotExist();
        assertThat(replacement.path()).exists();
    }

    @Test
    void neverDeletesExternalOtherUserTraversalOrReplacementPaths() throws IOException {
        AvatarFileStorage storage = storage();
        AvatarFileStorage.StoredAvatar replacement = storage.store(42L, new byte[] {2});
        AvatarFileStorage.StoredAvatar otherUser = storage.store(43L, new byte[] {3});

        Path sameUserExternalTarget = tempDir.resolve("avatars").resolve("outside.png");
        Files.createDirectories(sameUserExternalTarget.getParent());
        Files.write(sameUserExternalTarget, new byte[] {4});

        storage.deletePreviousIfOwned(42L, "https://cdn.example.com/avatar.png", replacement);
        storage.deletePreviousIfOwned(42L, otherUser.avatarUrl(), replacement);
        storage.deletePreviousIfOwned(
                42L, "/uploads/avatars/42/../outside.png", replacement);
        storage.deletePreviousIfOwned(42L, replacement.avatarUrl(), replacement);

        assertThat(otherUser.path()).exists();
        assertThat(sameUserExternalTarget).exists();
        assertThat(replacement.path()).exists();
    }

    private AvatarFileStorage storage() {
        return new AvatarFileStorage(tempDir.toString(), "/uploads");
    }
}
