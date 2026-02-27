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
6. `OPENAI_API_KEY` (if AI endpoints are enabled)

## Recommended Vars (Dev/Local)
1. `SPRING_PROFILES_ACTIVE=dev` or `local`
2. `SPRING_DATASOURCE_URL`
3. `SPRING_DATASOURCE_USERNAME`
4. `SPRING_DATASOURCE_PASSWORD`
5. `JWT_SECRET`
6. Optional proxy vars when OpenAI egress is blocked:
   - `OPENAI_PROXY_ENABLED=true`
   - `OPENAI_PROXY_URL=http://127.0.0.1:<port>`
   - `HTTPS_PROXY=http://127.0.0.1:<port>`
   - `HTTP_PROXY=http://127.0.0.1:<port>`

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

## Acceptance (Prod)
1. Log contains `The following 1 profile is active: "prod"`
2. Log contains `HikariPool-1 - Start completed`
3. No datasource bind errors
4. No port bind errors
5. `GET /health` returns 200
