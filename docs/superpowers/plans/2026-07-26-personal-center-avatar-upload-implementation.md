# Personal Center Avatar Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan.

**Goal:** Allow a signed-in user to upload a safe, persistent personal-center avatar with immediate UI feedback and production-ready storage controls.

**Architecture:** The browser validates JPEG/PNG/WebP input and normalizes it to a bounded PNG. A Spring application service applies a Redis user rate limit, independently validates and re-encodes JPEG/PNG content, writes a random PNG under the current user's avatar directory, updates `users.avatar_url`, and safely cleans up owned files. Docker and Nginx expose the same relative URL contract while persisting files across backend container recreation.

**Tech Stack:** Vue 3, TypeScript, Axios, native Canvas/ImageBitmap APIs, Spring Boot, MyBatis, Redis Lua, Java ImageIO, JUnit 5, Mockito, MockMvc, Node test runner, Docker Compose, Nginx.

**Global Constraints:**

- Continue on `codex/personal-center-1237`; do not modify the user's unrelated working-tree changes.
- Use test-driven development: add one focused failing test, confirm the intended failure, then add the minimum implementation.
- Do not add cropper, image codec, component-library, or client-state dependencies.
- The frontend accepts JPEG/PNG/WebP source files; the backend endpoint accepts only normalized JPEG/PNG bytes.
- Keep the public response contract as a relative `avatarUrl`; do not expose filesystem paths.
- Never delete an old URL unless it resolves inside `/uploads/avatars/{currentUserId}/`.

---

### Task 1: Lock down the image normalization boundary

**Files:**

- Create: `backend/src/test/java/com/personalenglishai/backend/service/avatar/AvatarImageNormalizerTest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/avatar/AvatarImageNormalizer.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`

**Interfaces:**

```java
@Component
public class AvatarImageNormalizer {
    public NormalizedAvatar normalize(MultipartFile file);
    public record NormalizedAvatar(byte[] bytes, int width, int height) {}
}
```

- [ ] Add a JUnit test that creates an in-memory JPEG wider than 1024px, normalizes it, and asserts PNG signature, maximum edge, and successful re-decoding.
- [ ] Run `./mvnw -Dtest=AvatarImageNormalizerTest test` from `backend` and confirm failure because the normalizer does not exist.
- [ ] Implement actual-format detection with `ImageIO` readers, pre-decode width/height checks, 5 MiB request limit, JPEG/PNG allowlist, maximum 4096px edge, and maximum 16,777,216 pixels.
- [ ] Re-encode decoded pixels to a metadata-free PNG with maximum edge 1024px and add avatar validation error codes.
- [ ] Add rejection tests for empty bytes, oversized bytes, MIME/header mismatch, direct WebP/unknown content, corrupt image bytes, and oversized dimensions.
- [ ] Run `./mvnw -Dtest=AvatarImageNormalizerTest test` and confirm all normalization tests pass.
- [ ] Commit with `test(user): 覆盖头像图片安全规范化`.

### Task 2: Persist only safe user-owned avatar files

**Files:**

- Create: `backend/src/test/java/com/personalenglishai/backend/service/avatar/AvatarFileStorageTest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/avatar/AvatarFileStorage.java`

**Interfaces:**

```java
@Component
public class AvatarFileStorage {
    public StoredAvatar store(Long userId, byte[] pngBytes);
    public void deleteNewFile(StoredAvatar avatar);
    public void deletePreviousIfOwned(Long userId, String oldAvatarUrl, StoredAvatar replacement);
    public record StoredAvatar(String avatarUrl, Path path) {}
}
```

- [ ] Add `@TempDir` tests asserting random `.png` storage under `avatars/{userId}`, a `/uploads/avatars/{userId}/...` URL, and no use of an original filename.
- [ ] Run `./mvnw -Dtest=AvatarFileStorageTest test` and confirm failure because storage does not exist.
- [ ] Implement normalized-root path resolution, directory creation, random UUID filenames, and byte writes using `app.upload-dir` and `app.upload-public-path`.
- [ ] Add tests proving cleanup deletes a prior file owned by the same user but never deletes external URLs, another user's file, the replacement itself, or a traversal attempt.
- [ ] Implement best-effort old-file cleanup with containment checks and warning logs; keep explicit new-file rollback deletion available to the application service.
- [ ] Run `./mvnw -Dtest=AvatarFileStorageTest test` and confirm all storage tests pass.
- [ ] Commit with `feat(user): 增加安全头像文件存储`.

### Task 3: Enforce a per-user Redis upload limit

**Files:**

- Create: `backend/src/test/java/com/personalenglishai/backend/service/avatar/AvatarUploadRateLimitServiceTest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/avatar/AvatarUploadRateLimitService.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`

**Interfaces:**

```java
@Service
public class AvatarUploadRateLimitService {
    public void consume(Long userId);
}
```

- [ ] Add Mockito tests asserting key `user:avatar:upload:hour:{userId}`, 3600-second TTL, 10-request limit, and rejection when Lua returns limited or null.
- [ ] Run `./mvnw -Dtest=AvatarUploadRateLimitServiceTest test` and confirm failure because the service does not exist.
- [ ] Implement an atomic Redis Lua `INCR`/`EXPIRE` script following the existing email-verification rate-limit pattern and fail closed.
- [ ] Run `./mvnw -Dtest=AvatarUploadRateLimitServiceTest test` and confirm all rate-limit tests pass.
- [ ] Commit with `feat(user): 增加头像上传频率限制`.

### Task 4: Orchestrate database update and rollback

**Files:**

- Create: `backend/src/test/java/com/personalenglishai/backend/service/avatar/AvatarServiceTest.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/service/avatar/AvatarService.java`
- Create: `backend/src/main/java/com/personalenglishai/backend/controller/dto/AvatarUploadResponse.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/mapper/UserMapper.java`
- Modify: `backend/src/main/resources/mapper/UserMapper.xml`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`

**Interfaces:**

```java
@Service
public class AvatarService {
    public AvatarUploadResponse upload(Long userId, MultipartFile file);
}

public record AvatarUploadResponse(String avatarUrl) {}
```

- [ ] Add service tests for user missing, success, storage failure, zero-row database update, database exception, and successful cleanup after database update.
- [ ] Run `./mvnw -Dtest=AvatarServiceTest test` and confirm failure because the application service does not exist.
- [ ] Add `UserMapper.updateAvatarUrl(id, avatarUrl)` and implement the ordered flow: find user, consume rate limit, normalize, store, update row, roll back new file on database failure, then best-effort old-file cleanup.
- [ ] Ensure a zero-row update is treated as failure and never returns a URL.
- [ ] Run `./mvnw -Dtest=AvatarServiceTest test` and confirm all orchestration tests pass.
- [ ] Commit with `feat(user): 串联头像上传与资料更新`.

### Task 5: Expose the authenticated multipart endpoint

**Files:**

- Modify: `backend/src/test/java/com/personalenglishai/backend/controller/UserProfileControllerTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/controller/UserProfileController.java`
- Modify: `backend/src/test/java/com/personalenglishai/backend/common/web/GlobalExceptionHandlerTest.java`
- Modify: `backend/src/main/java/com/personalenglishai/backend/common/web/GlobalExceptionHandler.java`

**Interfaces:**

```http
POST /api/users/me/profile/avatar
Content-Type: multipart/form-data
field: file
```

- [ ] Add a MockMvc multipart test asserting the request attribute user ID reaches `AvatarService` and the response contains `data.avatarUrl`.
- [ ] Run `./mvnw -Dtest=UserProfileControllerTest test` and confirm a 404 or missing-bean failure for the new endpoint.
- [ ] Inject `AvatarService`, add the multipart controller method, and return `ApiResponse<AvatarUploadResponse>` with the trace ID.
- [ ] Add exception-handler tests for multipart size overflow and avatar rate limit, then add the minimal stable error mapping.
- [ ] Keep the existing larger global multipart ceiling required by document imports; enforce 5 MiB in the avatar normalizer and 6 MiB at the production Nginx route.
- [ ] Run `./mvnw -Dtest=UserProfileControllerTest,GlobalExceptionHandlerTest test` and confirm all endpoint tests pass.
- [ ] Commit with `feat(api): 开放个人头像上传接口`.

### Task 6: Normalize source images in the browser

**Files:**

- Create: `web/tests/avatarImage.test.ts`
- Create: `web/src/components/personal-center/avatarImage.ts`

**Interfaces:**

```ts
export const AVATAR_MAX_SOURCE_BYTES = 5 * 1024 * 1024
export function validateAvatarFile(file: File): string | null
export function resolveAvatarOutputSize(width: number, height: number): { width: number; height: number }
export async function normalizeAvatarFile(file: File): Promise<File>
```

- [ ] Add Node tests for the accepted MIME allowlist, 5 MiB boundary, error messages, and 1024px proportional output sizing.
- [ ] Run `npx tsx --test tests/avatarImage.test.ts` from `web` and confirm failure because the module does not exist.
- [ ] Implement pure validation and output-size helpers.
- [ ] Add injectable decode/encode boundary tests proving JPEG/PNG/WebP inputs return a PNG `File`, resources are released, and encoding failure is surfaced.
- [ ] Implement native `createImageBitmap` plus Canvas PNG encoding with an `HTMLImageElement` fallback when necessary; do not add a dependency.
- [ ] Run `npx tsx --test tests/avatarImage.test.ts` and confirm all browser-normalization unit tests pass.
- [ ] Commit with `feat(ui): 增加头像浏览器规范化`.

### Task 7: Upload and display the avatar in the personal center

**Files:**

- Create: `web/tests/avatarUploadApi.test.ts`
- Modify: `web/src/api/user.ts`
- Modify: `web/src/pages/app/PersonalCenterPage.vue`

**Interfaces:**

```ts
export interface AvatarUploadResponse {
  code?: string
  message?: string
  data?: { avatarUrl?: string }
}

userApi.uploadAvatar(file: File): Promise<AvatarUploadResponse>
```

- [ ] Add an API test with an Axios adapter that asserts `POST /users/me/profile/avatar`, multipart `file` content, and structured response parsing.
- [ ] Run `npx tsx --test tests/avatarUploadApi.test.ts` from `web` and confirm failure because `uploadAvatar` does not exist.
- [ ] Implement the `FormData` API method without manually setting the multipart boundary.
- [ ] Add a focused Playwright component/page contract covering avatar image rendering, fallback after image error, hidden input triggering, and the nickname pencil remaining separate.
- [ ] Run the focused UI test and confirm it fails against the initial-only avatar button.
- [ ] Update the personal-center header with a real image/fallback, camera hover/focus overlay, hidden input, loading lock, normalization/upload flow, response update, input reset, and success/error/rate-limit toasts.
- [ ] Keep preview mode deterministic by replacing the selected image locally without calling a production endpoint.
- [ ] Run the focused API and UI tests and confirm they pass.
- [ ] Commit with `feat(ui): 支持个人中心上传头像`.

### Task 8: Make uploaded avatars survive production deployment

**Files:**

- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `deploy/nginx/nginx.conf`
- Modify: `deploy/nginx/ecs-80-only.conf`
- Modify: `deploy/beta/nginx.beta.conf`
- Modify: `docs/superpowers/specs/2026-07-26-personal-center-avatar-upload-design.md`

- [ ] Add `APP_UPLOAD_DIR=/data/uploads` to the backend container, mount named volume `peai_uploads_data:/data/uploads`, and document the variable in `.env.example`.
- [ ] Add an exact avatar upload location with `client_max_body_size 6M` before the general API location in all active Nginx variants.
- [ ] Add `/uploads/` proxy locations with `X-Content-Type-Options: nosniff`; preserve the beta SPA fallback for all other paths.
- [ ] Run `docker compose config` and confirm the backend mount and environment resolve correctly.
- [ ] If local Nginx is installed, run `nginx -t` for each variant; otherwise perform a line-by-line config review and explicitly record that the binary check was unavailable.
- [ ] Search for unresolved `.webp` output examples, placeholder steps, and missing environment documentation.
- [ ] Commit with `chore(deploy): 持久化并代理用户头像`.

### Task 9: Full verification and handoff

**Files:**

- Modify only if verification reveals a scoped defect.

- [ ] Run focused backend tests: `./mvnw -Dtest=AvatarImageNormalizerTest,AvatarFileStorageTest,AvatarUploadRateLimitServiceTest,AvatarServiceTest,UserProfileControllerTest,GlobalExceptionHandlerTest test`.
- [ ] Run the broader backend suite appropriate to the changed module: `./mvnw test`.
- [ ] Run frontend unit tests: `npx tsx --test tests/avatarImage.test.ts tests/avatarUploadApi.test.ts`.
- [ ] Run `npm run build` from `web`.
- [ ] Use the in-app browser at `/app/me?tab=overview` to verify click, file selection, loading, immediate image display, refresh persistence, invalid format feedback, same-file reselection, image-error fallback, and keyboard operation.
- [ ] Inspect browser console and relevant requests for errors and capture a final screenshot.
- [ ] Review `git diff --check`, `git status --short`, and the complete scoped diff.
- [ ] Decide whether documentation needs another update and whether the branch is safe to merge to `main`.
- [ ] Commit any final scoped fixes using Conventional Commits; do not merge to `main` without an explicit request.
