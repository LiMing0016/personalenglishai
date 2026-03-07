# Beta deploy (Aliyun ECS)

This folder is a minimal beta stack for quick testing with friends.
It runs frontend + backend under one gateway domain and keeps API as `/api`.

## Files

- `docker-compose.beta.yml`: beta services (backend, redis, context-sidecar, gateway)
- `nginx.beta.conf`: serves `web/dist` and proxies `/api` to backend
- `.env.beta.example`: beta environment template
- `deploy-beta.sh`: one-command beta deploy
- `check-beta.sh`: quick status and log check

## 0) Branch and safety

You are currently on `main` with many local changes.
For safer rollback in ECS, deploy from a dedicated beta branch.

## 1) Prepare ECS once

Install Docker + Compose plugin.

## 2) Prepare env file

```bash
cd /path/to/personalenglishai
cp deploy/beta/.env.beta.example deploy/beta/.env.beta
# edit deploy/beta/.env.beta
```

Important values:

- `SPRING_DATASOURCE_*` should point to a **beta database**.
- `JWT_SECRET` must be a long random string.
- `OPENAI_API_KEY` should be your beta/testing key.
- `APP_BASE_URL` should be your beta URL or ECS IP.

## 3) Deploy beta

```bash
cd /path/to/personalenglishai
chmod +x deploy/beta/deploy-beta.sh deploy/beta/check-beta.sh
./deploy/beta/deploy-beta.sh
```

The script will:

1. Build frontend dist with `node:20-alpine`
2. Build backend image from root `Dockerfile`
3. Start all beta services with docker compose
4. Run quick checks (`/health`, `/api/ping`)

## 4) Validate

```bash
./deploy/beta/check-beta.sh
curl -i http://<ecs-ip>/health
curl -i http://<ecs-ip>/api/ping
```

## 5) Update after code changes

```bash
git pull
./deploy/beta/deploy-beta.sh
```

## 6) Stop beta

```bash
docker compose --env-file deploy/beta/.env.beta -f deploy/beta/docker-compose.beta.yml down
```

## Notes

- Only expose `80/443` in security group.
- Do not expose MySQL/Redis to public network.
- If you already run another service on port 80, set `BETA_HTTP_PORT` in `.env.beta` to another port.
