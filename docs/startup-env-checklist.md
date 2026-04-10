# Startup Env Checklist

## Goal
Keep env values consistent across:
1. IDEA Run Configuration
2. local `backend/.env` (template only)
3. production environment variables

`backend/.env` is for local reference and should not be committed.

## Required Vars (Prod)
1. `SPRING_PROFILES_ACTIVE=prod`
2. `SPRING_DATASOURCE_URL`
3. `SPRING_DATASOURCE_USERNAME`
4. `SPRING_DATASOURCE_PASSWORD`
5. `JWT_SECRET` (32+ bytes)
6. Provider-neutral AI vars（if AI endpoints are enabled）
   - `AI_PROVIDER_ACTIVE=openai|kimi|qwen`
   - `AI_PROVIDER_<PROVIDER>_API_KEY`
   - `AI_PROVIDER_<PROVIDER>_BASE_URL`
   - `AI_PROVIDER_<PROVIDER>_MODEL`

## Recommended Vars (Dev/Local)
1. `SPRING_PROFILES_ACTIVE=dev` or `local`
2. `SPRING_DATASOURCE_URL`
3. `SPRING_DATASOURCE_USERNAME`
4. `SPRING_DATASOURCE_PASSWORD`
5. `JWT_SECRET`
6. AI provider vars：
   - `AI_PROVIDER_ACTIVE=openai|kimi|qwen`
   - `AI_PROVIDER_OPENAI_API_KEY`
   - `AI_PROVIDER_KIMI_API_KEY`
   - `AI_PROVIDER_QWEN_API_KEY`
7. Optional proxy vars when OpenAI-compatible egress is blocked:
   - `OPENAI_PROXY_ENABLED=true`
   - `OPENAI_PROXY_URL=http://127.0.0.1:<port>`
   - `HTTPS_PROXY=http://127.0.0.1:<port>`
   - `HTTP_PROXY=http://127.0.0.1:<port>`

## Current Session AI Provider
写作页顶部的模型选择器会为当前 `docId` 保存一次会话级 provider 选择：

1. 可选值：`openai` / `kimi` / `qwen`
2. 同一篇作文刷新后会恢复上次选择
3. 切换 provider 时，只清空当前作文的 AI 会话上下文，不清空作文正文
4. 前端请求显式传 `aiProvider` 时，优先级高于系统默认 `AI_PROVIDER_ACTIVE`

## IDEA Run Configuration
1. Module: `backend`
2. Active profile: `dev`/`local`/`prod`
3. Put env vars in `Environment variables` (single source of truth)
4. Do not rely on file auto-loading unless explicitly configured

## Acceptance (Dev/Local)
1. Log contains `The following 1 profile is active: "dev"` (or `local`)
2. Log contains `HikariPool-1 - Start completed`
3. No `using password: NO`
4. `GET /health` returns 200
5. 写作页切换 `OpenAI / Kimi / 千问` 后，请求日志能看到对应 `provider/model/baseUrl`

## Acceptance (Prod)
1. Log contains `The following 1 profile is active: "prod"`
2. Log contains `HikariPool-1 - Start completed`
3. No datasource bind errors
4. No port bind errors
5. `GET /health` returns 200
