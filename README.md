# Personal English AI

前后端分离的英语学习 Web 应用。后端 Spring Boot + JWT，前端 Vue 3 + Vite + TypeScript。

## 401/403 边界

| 状态码 | 含义         | 前端行为 |
|--------|--------------|----------|
| **401** | 未认证       | 清除本地 token，并跳转登录页 |
| **403** | 已认证无权限 | 仅提示「无权限」，不跳转、不清 token |

详见 [docs/roadmap.md](docs/roadmap.md) 阶段 0.5 与 [web/README.md](web/README.md) 安全与鉴权章节。

## 安全功能

### 滑动拼图验证码

登录时需完成滑动拼图验证，防止自动化登录攻击。

- **实现**：自托管，Java2D 生成背景图+拼图块，无第三方依赖
- **流程**：`GET /api/v1/auth/captcha` → 用户拖动拼图 → `POST /api/v1/auth/captcha/verify` → 获取 token → 携带 token 登录
- **缓存**：内存 ConcurrentHashMap（验证码 2 分钟过期，token 1 分钟过期）
- **容差**：±4px

| 端点 | 说明 |
|------|------|
| `GET /api/v1/auth/captcha` | 获取验证码图片（背景+拼图块 base64） |
| `POST /api/v1/auth/captcha/verify` | 验证滑动位置，返回一次性 captchaToken |
| `POST /api/v1/auth/login` | 登录时需携带 captchaToken 字段 |

## 仓库结构

- `backend/` — Spring Boot 后端
- `web/` — Vue 3 前端
- `docs/` — 文档与路线图
- `deploy/` — 部署相关
