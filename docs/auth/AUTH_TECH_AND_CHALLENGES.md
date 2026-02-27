# 注册与登录：技术选型与难点总结

## 一、所用技术

### 1. 注册（POST /api/v1/auth/register）

| 技术/组件 | 用途 |
|-----------|------|
| **Spring Boot 3.x + Jakarta Validation** | REST 接口、`@Valid` 参数校验 |
| **Jakarta Validation** | `@NotBlank`、`@Email`、`@Size` 校验 email、password、nickname |
| **Spring Security Crypto** | `BCryptPasswordEncoder` 对密码做哈希，存入 `password_hash` |
| **MyBatis** | `UserMapper` 持久化，`findByEmail`、`insert` |
| **MySQL** | `users` 表，`email` 唯一索引 |
| **ApiResponse + ErrorCode** | 统一返回体 `code/message/data/traceId`，错误码如 `409001`、`400001` |
| **GlobalExceptionHandler** | 集中处理 `MethodArgumentNotValidException`、`DuplicateKeyException`、`BizException` |
| **MDC** | 为响应设置 `traceId`，便于链路排查 |

**流程简要：**  
Controller 接收 `RegisterRequest` → `@Valid` 校验 → `AuthService.register` 内邮箱规范化（trim + toLowerCase）、唯一性校验、BCrypt 哈希、插入用户 → 返回 201 + `userId`。

---

### 2. 登录（POST /api/v1/auth/login）

| 技术/组件 | 用途 |
|-----------|------|
| **Jakarta Validation** | `@NotBlank`、`@Email` 校验 email、password |
| **Jackson** | `@JsonIgnoreProperties(ignoreUnknown = false)`，禁止请求体出现未知字段（如 `nickname`） |
| **BCrypt** | `PasswordEncoder.matches` 校验密码与 `password_hash` |
| **JJWT (io.jsonwebtoken)** | `JwtUtil`：HMAC-SHA 签名、生成/解析 JWT，claims 含 `userId`（subject）、`nickname` |
| **UserMapper.findByEmail** | 按邮箱查用户，邮箱为登录唯一标识 |
| **AuthService.login** | 邮箱规范化、查用户、校验密码、生成 token，失败抛 `BizException(AUTH_LOGIN_FAILED)` |
| **GlobalExceptionHandler** | 处理 `HttpMessageNotReadableException`（未知字段等）、`BizException`（401001） |

**流程简要：**  
Controller 接收 `LoginRequest`（仅 email、password）→ `@Valid` 校验 → `AuthService.login` 查用户、BCrypt 校验、生成 JWT → 返回 200 + `{ token, tokenType: "Bearer", expiresIn }`。

---

### 3. JWT 鉴权与受保护接口

| 技术/组件 | 用途 |
|-----------|------|
| **OncePerRequestFilter（JwtAuthenticationFilter）** | 唯一鉴权入口：白名单放行，非白名单校验 `Authorization: Bearer` |
| **JwtUtil** | `validateToken`、`getUserIdFromToken`、`getNicknameFromToken` |
| **ObjectMapper** | Filter 内将 `ApiResponse` 序列化为 JSON，统一 401 响应体 |
| **request.setAttribute("userId")** | 鉴权通过后写入请求属性，Controller 通过 `@RequestAttribute("userId")` 获取 |
| **GET /api/users/me/profile** | 受保护接口示例，需 JWT；从 `userId` 查库返回 `userId/email/nickname` |

**白名单：** `/api/v1/auth/**`、`/health`、`/api/ping`。其余路径无 token 或 token 无效 → 401 + `ApiResponse(code="401001", message="未登录或登录已过期")`。

---

## 二、解决的难点

### 1. 公开接口被误拦导致 401

- **现象：** 注册、登录等无需 token 的接口，因 Filter/Interceptor 对所有请求做 JWT 校验，无 `Authorization` 就 401。
- **处理：** 在 `JwtAuthenticationFilter` 最前面做**白名单**判断，`/api/v1/auth/**`、`/health`、`/api/ping` 直接 `filterChain.doFilter` 放行，不做任何 token 校验。只有非白名单路径才校验 JWT。

---

### 2. 登录标识不唯一导致 TooManyResultsException

- **现象：** 曾用 `nickname` 作为登录标识，`findByNickname` 可能查出多行，触发 MyBatis `TooManyResultsException`。
- **处理：** 登录改为以 **email** 为唯一标识，使用 `UserMapper.findByEmail`。邮箱在库中有唯一约束，保证至多一条；同时与注册的“邮箱唯一”一致。

---

### 3. 登录请求体多传字段仍返回 200

- **现象：** 请求体多传 `nickname` 等字段时，若不做限制，接口仍 200，不利于前端约束与排查。
- **处理：** 在 `LoginRequest` 上使用 `@JsonIgnoreProperties(ignoreUnknown = false)`，Jackson 反序列化遇到未知字段即抛 `UnrecognizedPropertyException`；`GlobalExceptionHandler` 捕获 `HttpMessageNotReadableException`，根因为该异常时返回 400 + `400001` + `"不允许的字段: xxx"`。登录请求体**仅允许** `email`、`password`。

---

### 4. 无 token 访问受保护接口返回 500 或空 401

- **现象：** 无 `Authorization` 访问 `/api/users/me/profile` 时，曾出现 500，或 401 无统一 JSON 体。
- **处理：**  
  - 非白名单路径且无 `Authorization` 或非 `Bearer` → Filter **直接** 返回 401，不进入 Controller。  
  - 401 响应统一为 `ApiResponse`：`code="401001"`、`message="未登录或登录已过期"`，Filter 内用 `ObjectMapper` 序列化后写入 response，并设置 `Content-Type: application/json`。  
这样避免 500，且 401 结构与其他接口一致。

---

### 5. 占位登录返回 501、路由冲突与多套 Auth 并存

- **现象：** 存在占位 login（返回 501）、旧版 `AuthController`（`/api/auth/**`）与 v1（`/api/v1/auth/**`）并存，Postman 调用时路由不确定，出现 404/501/500 混用。
- **处理：**  
  - 删除旧版 `AuthController`，仅保留 **v1**：`AuthControllerV1`（`/api/v1/auth`）。  
  - 登录改为完整实现：email + password → BCrypt 校验 → 签发 JWT，不再返回 501。  
  - 鉴权只依赖 `JwtAuthenticationFilter`，移除对 `JwtInterceptor` 的注册，避免多套鉴权逻辑打架。

---

### 6. 邮箱已注册、请求体格式等客户端错误被包成 500

- **现象：** 如重复邮箱、JSON 格式错误、缺少 body、未知字段等，若未专门处理，会落入 `Exception` 处理器，统一 500。
- **处理：**  
  - `DuplicateKeyException` → 409 + `AUTH_EMAIL_EXISTS`（邮箱已注册）。  
  - `MethodArgumentNotValidException` → 400 + `400001`，`message` 含字段级错误。  
  - `HttpMessageNotReadableException` → 400 + `400001`（含“不允许的字段”等细化信息）。  
  - `BizException` 按 `ErrorCode` 映射 HTTP 状态（如 409、401）。  
这样把“客户端问题”与“服务端异常”区分开，避免 500 滥用。

---

### 7. 受保护接口的 userId 来源不可信

- **现象：** 若通过 `X-User-Id` 等 header 传入 userId，可伪造身份。
- **处理：**  
  - 受保护接口的 `userId` **仅**来自 JWT 解析。Filter 校验 token 通过后，从 `JwtUtil` 取出 `userId`（及 `nickname`），`request.setAttribute("userId", ...)`。  
  - Controller 使用 `@RequestAttribute("userId")` 获取，禁止通过 header 传入。  
  - `GET /api/users/me/profile`、`PATCH /api/users/me/profile/stage` 等均按此方式拿当前用户。

---

### 8. 登录无 body 时 500（HttpMessageNotReadableException）

- **现象：** 占位 login 曾声明 `@RequestBody` 必填，无 body 时触发 `HttpMessageNotReadableException`，被全局 handler 转 500 或非预期状态码。  
- **处理：**  
  - 登录改为真正实现，必填 body（`LoginRequest`）。  
  - 全局对 `HttpMessageNotReadableException` 统一转 400 + `400001`，避免 500。  
  - 无 body、格式错误、未知字段等均走该分支，与“参数校验”一致对待。

---

## 三、相关文件速查

| 角色 | 路径 |
|------|------|
| 注册/登录 Controller | `controller/auth/v1/AuthControllerV1` |
| 注册/登录 DTO | `controller/auth/dto/RegisterRequest`、`RegisterResponse`、`LoginRequest`、`LoginResponse` |
| 认证服务 | `service/auth/AuthService`、`impl/AuthServiceImpl` |
| JWT 工具 | `util/JwtUtil` |
| 鉴权 Filter | `common/filter/JwtAuthenticationFilter` |
| 全局异常 | `common/web/GlobalExceptionHandler` |
| 错误码 | `common/error/ErrorCode`、`BizException` |

接口契约与验收命令见 [Auth 模块验收说明](./README.md)。
