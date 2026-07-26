package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class AvatarFileStorage {

    private static final Logger log = LoggerFactory.getLogger(AvatarFileStorage.class);

    private final Path uploadRoot;
    private final String publicUploadPath;

    public AvatarFileStorage(
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.upload-public-path:/uploads}") String publicUploadPath) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicUploadPath = normalizePublicPath(publicUploadPath);
    }

    public StoredAvatar store(Long userId, byte[] pngBytes) {
        Path userDirectory = userDirectory(userId);
        String filename = UUID.randomUUID() + ".png";
        Path target = resolveOwnedFile(userDirectory, filename);
        Path temporary = resolveOwnedFile(userDirectory, "." + filename + ".tmp");

        try {
            Files.createDirectories(userDirectory);
            Files.write(temporary, pngBytes);
            moveIntoPlace(temporary, target);
            String avatarUrl = publicUploadPath + "/avatars/" + userId + "/" + filename;
            return new StoredAvatar(avatarUrl, target);
        } catch (IOException e) {
            deleteQuietly(temporary, "头像临时文件");
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "头像文件保存失败");
        }
    }

    public void deleteNewFile(StoredAvatar avatar) {
        if (avatar == null) {
            return;
        }
        deleteQuietly(avatar.path(), "回滚头像文件");
    }

    public void deletePreviousIfOwned(Long userId, String oldAvatarUrl, StoredAvatar replacement) {
        if (oldAvatarUrl == null || oldAvatarUrl.isBlank()) {
            return;
        }

        String ownedUrlPrefix = publicUploadPath + "/avatars/" + userId + "/";
        if (!oldAvatarUrl.startsWith(ownedUrlPrefix)) {
            return;
        }

        String relativeFilename = oldAvatarUrl.substring(ownedUrlPrefix.length());
        if (relativeFilename.isBlank()) {
            return;
        }

        Path userDirectory = userDirectory(userId);
        Path oldPath = userDirectory.resolve(relativeFilename).toAbsolutePath().normalize();
        if (!oldPath.startsWith(userDirectory)
                || oldPath.equals(userDirectory)
                || (replacement != null && oldPath.equals(replacement.path()))) {
            return;
        }

        deleteQuietly(oldPath, "旧头像文件");
    }

    private Path userDirectory(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Path directory = uploadRoot.resolve("avatars").resolve(userId.toString())
                .toAbsolutePath().normalize();
        if (!directory.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("avatar directory escapes upload root");
        }
        return directory;
    }

    private Path resolveOwnedFile(Path userDirectory, String filename) {
        Path path = userDirectory.resolve(filename).toAbsolutePath().normalize();
        if (!path.startsWith(userDirectory) || path.equals(userDirectory)) {
            throw new IllegalArgumentException("avatar path escapes user directory");
        }
        return path;
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target);
        }
    }

    private void deleteQuietly(Path path, String label) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("{}清理失败 path={}", label, path);
        }
    }

    private String normalizePublicPath(String path) {
        String normalized = path == null || path.isBlank() ? "/uploads" : path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record StoredAvatar(String avatarUrl, Path path) {
    }
}
