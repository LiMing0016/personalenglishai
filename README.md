# Personal English AI

前后端分离的英语学习 Web 应用。后端 Spring Boot + JWT，前端 Vue 3 + Vite + TypeScript。

## 401/403 边界

| 状态码 | 含义         | 前端行为 |
|--------|--------------|----------|
| **401** | 未认证       | 清除本地 token，并跳转登录页 |
| **403** | 已认证无权限 | 仅提示「无权限」，不跳转、不清 token |

详见 [docs/roadmap.md](docs/roadmap.md) 阶段 0.5 与 [web/README.md](web/README.md) 安全与鉴权章节。

## 仓库结构

- `backend/` — Spring Boot 后端
- `web/` — Vue 3 前端
- `docs/` — 文档与路线图
- `deploy/` — 部署相关
