# 已实现接口字段级清单（按模块）

本清单仅基于当前仓库代码整理，口径日期：2026-03-05。

## 1. 通用规则（现状）

| 项目 | 当前实现 |
|---|---|
| 前端 API 基础路径 | `/api` |
| 成功响应格式 | 混用：部分接口为 `ApiResponse<T>`，部分为裸 JSON DTO，少量为纯字符串 |
| 401 行为 | 前端尝试 refresh；失败后清 token 跳 `/login` |
| 403 行为 | 前端仅提示“无权限访问” |
| JWT 白名单 | `/api/v1/auth/*`、`/health`、`/api/ping` |
| 开发环境特例 | `/api/ai/command` 在 `dev/local` 且无 Bearer 时允许匿名（mock tenant） |

## 2. Auth 模块（`/api/v1/auth/*`，免 JWT）

### 2.1 Captcha

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `GET /captcha` | 无 | `ApiResponse<{ captchaId, bgImage, pieceImage }>` |
| `POST /captcha/verify` | `captchaId:string(必填)` `x:int` | `ApiResponse<{ verified:boolean, captchaToken?:string }>` |

### 2.2 邮箱注册与登录

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /register` | `email:string(必填,email)` `password:string(必填,>=8,含大小写+数字)` `nickname:string(必填,1-50)` | `201 + ApiResponse<{ userId:number }>` |
| `POST /login` | `email:string(必填,email)` `password:string(必填)` `captchaToken:string(必填)` | `ApiResponse<{ token:string, tokenType:string, expiresIn:number }>` |

### 2.3 刷新与登出

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /refresh` | 无 body；从 cookie 读取 `refresh_token` | `ApiResponse<{ token, tokenType, expiresIn }>` 并更新 refresh cookie |
| `POST /logout` | 无 | `ApiResponse<void>` 并清空 refresh cookie |

### 2.4 邮箱验证与找回密码

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `GET /verify-email` | query: `token:string(必填)` | `ApiResponse<{ status:string }>` |
| `POST /resend-verification` | body: `{ email?: string }` | `ApiResponse<void>` |
| `POST /forgot-password` | body: `{ email?: string }` | `ApiResponse<void>` |
| `GET /reset-password/validate` | query: `token:string(必填)` | `ApiResponse<{ status:string }>` |
| `POST /reset-password` | `token:string(必填)` `password:string(必填,>=8,含大小写+数字)` | `ApiResponse<void>` |

### 2.5 手机验证码登录/注册

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /sms/send` | `phone:string(必填,手机号)` `purpose:string(必填,login/register)` | `ApiResponse<void>` |
| `POST /phone/login` | `phone:string(必填)` `mode:string(必填,otp/password)` `code?:string` `password?:string` | `ApiResponse<{ token, tokenType, expiresIn }>` |
| `POST /phone/register` | `phone:string(必填)` `code:string(必填,6位)` `nickname:string(必填,1-50)` | `201 + ApiResponse<{ token, tokenType, expiresIn }>` |

## 3. 用户中心模块（JWT 必需）

### 3.1 个人资料

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `GET /api/users/me/profile` | 无 | `ApiResponse<{ userId, email, nickname, studyStage, aiMode, emailVerified, phone, phoneVerified, avatarUrl, registerSource, createdAt }>` |
| `PATCH /api/users/me/profile/nickname` | `nickname:string(必填,1-32)` | `204 No Content` |
| `PATCH /api/users/me/profile/stage` | `studyStage?:string` | `204 No Content` |

### 3.2 能力与统计

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `GET /api/users/me/profile/ability` | 无 | `ApiResponse<{ taskScore, coherenceScore, grammarScore, vocabularyScore, structureScore, varietyScore, assessedScore, confidence, sampleCount, updatedAt }>` |
| `GET /api/users/me/profile/stats` | 无 | `ApiResponse<{ totalEssays, averageScore, bestScore, studyDays, memberSince }>` |

### 3.3 账号密码

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /api/users/me/password` | `currentPassword:string(必填)` `newPassword:string(必填,>=8)` | `ApiResponse<void>` |

## 4. 写作模块（`/api/writing/*`，JWT 必需）

### 4.1 评测

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /evaluate` | `essay:string(必填,业务规则20~500词)` `aiHint?:string` `mode?:free/exam` `lang?:string` `taskPrompt?:string` | `WritingEvaluateResponse`（裸 DTO） |
| `POST /evaluate/submit` | 同上 | `WritingEvaluateTaskResponse`（裸 DTO，`status` 初始 processing） |
| `GET /evaluate/tasks/{requestId}` | path: `requestId:string` | `WritingEvaluateTaskResponse`（裸 DTO；非本人或不存在返回 404） |

`WritingEvaluateResponse` 关键字段：

- `requestId` `mode` `source`
- `grades: map<string,string>`
- `dimensionScores: map<string,int>`
- `analysis: map<string,{ quote, strength_quote, weakness_quote, strength, weakness, suggestion }>`
- `priority_focus: string[]`
- `priority_focus_detail: { dimension, reason, action_item }`
- `score: { overall, task, coherence, lexical, grammar }`
- `gaokao_score: { score, max_score, band }`
- `improvement: { previous_score, current_score, delta, message }`
- `summary`
- `errors: [{ id, type, category, severity, span:{start,end}, original, suggestion, reason }]`

### 4.2 Chat 与润色

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /chat` | `essay:string(必填)` `instruction:string(必填)` `lang?:string` `mode?:string` `aiHint?:string` `selectedText?:string` | `WritingChatResponse`（裸 DTO） |
| `POST /polish` | `original:string(必填)` `tier:string(必填,basic/steady/advanced/perfect)` `context?:string` `reason?:string` | `PolishResponse`（裸 DTO） |

`WritingChatResponse` 字段：`requestId` `assistantMessage` `rewrite?:{ fullText, summary }` `resultText?`。

### 4.3 历史与收藏

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `GET /history` | query: `page:int=0` `size:int=10`（后端最大 50） | `EvaluationHistoryResponse`（裸 DTO） |
| `GET /history/{id}` | path: `id:number` | `EvaluationDetailResponse`（裸 DTO） |
| `POST /history/{id}/favorite` | path: `id:number` | `{ favorited:boolean }`（裸 JSON） |

`EvaluationHistoryResponse` 字段：`items[]`、`total`。`items` 包含 `id, mode, gaokao_score, max_score, band, overall_score, essay_preview, created_at, favorited`。

## 5. 文档模块（`/api/docs/*`，JWT 必需）

| 接口 | 请求字段 | 成功响应字段 |
|---|---|---|
| `POST /api/docs` | `{ title?:string, content?:string }` | `{ docId:string, latestRevision:number }` |
| `POST /api/docs/{docId}/revisions` | path:`docId` body:`{ expectedLatestRevision:number, content?:string }` | `{ latestRevision:number }` |
| `GET /api/docs/{docId}` | path:`docId` | `{ title:string, latestRevision:number, content:string }` |
| `GET /api/docs/{docId}/revisions/{rev}` | path:`docId` `rev:number` | `{ title:string, revision:number, content:string }` |
| `DELETE /api/docs/{docId}` | path:`docId` | `204 No Content` |

## 6. Rubric 模块

| 接口 | 鉴权 | 请求字段 | 成功响应字段 |
|---|---|---|---|
| `GET /api/v1/rubric/active` | JWT 必需 | query: `stage:string=highschool` `mode:string=free/exam` | `RubricActiveResponse`（裸 DTO） |

`RubricActiveResponse` 字段：

- `rubricKey`
- `mode`
- `dimensions[]: { dimensionKey, displayName, levels[] }`
- `levels[]: { level, score, criteria }`

## 7. AI 模块

### 7.1 AI Command

| 接口 | 鉴权 | 请求字段 | 成功响应字段 |
|---|---|---|---|
| `POST /api/ai/command` | JWT 必需；仅 `dev/local` 可匿名调用 | `AICommandRequest` | `AICommandResponse` |

`AICommandRequest` 字段：`apiVersion` `intent(必填)` `mode?` `instruction?` `contextRefs?` `constraints?` `output?`。

`AICommandResponse` 字段：`traceId` `status(success/failed/running)` `result:{ format, apply, explain[] }` `finalResult:{ content, diff?, usage? }`。

### 7.2 AI Generate（旧接口）

| 接口 | 鉴权 | 请求字段 | 成功响应字段 |
|---|---|---|---|
| `POST /api/ai/generate` | JWT 必需 | header:`X-User-Id?:number(默认1)` body:`{ prompt?:string }` | `{ content:string }` |

## 8. 系统可用性接口

| 接口 | 鉴权 | 成功响应 |
|---|---|---|
| `GET /api/ping` | 免 JWT | `text/plain: "ok"` |
| `GET /api/db/ping` | JWT 必需 | `text/plain: "db-ok"` |
| `GET /health` | 免 JWT | `{ status:"ok", service:"backend", timestamp }` |

## 9. 错误码口径（现状）

全局异常返回 `ApiResponse<{ code, message, traceId }>` 结构，常见错误码：

- `400001` 参数验证失败
- `401001` 未认证/登录失败
- `401002` refresh 无效
- `403001` 文档非 owner
- `404001` 文档不存在
- `409001` 邮箱已注册
- `429001` 登录频率限制
- `400010` 作文过短
- `400011` 作文过长
