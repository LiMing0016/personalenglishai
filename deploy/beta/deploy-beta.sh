#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BETA_DIR="$ROOT_DIR/deploy/beta"
ENV_FILE="${1:-$BETA_DIR/.env.beta}"
COMPOSE_FILE="$BETA_DIR/docker-compose.beta.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] env file not found: $ENV_FILE"
  echo "Create it first: cp $BETA_DIR/.env.beta.example $BETA_DIR/.env.beta"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker is required"
  exit 1
fi

echo "[1/4] Build frontend dist with Node container"
docker run --rm -v "$ROOT_DIR/web:/app" -w /app node:20-alpine sh -lc "npm ci && npm run build"

echo "[2/4] Start beta stack"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build

echo "[3/4] Service status"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps

echo "[4/4] Quick health check"
HTTP_PORT="$(grep -E '^BETA_HTTP_PORT=' "$ENV_FILE" | tail -n1 | cut -d'=' -f2 || true)"
HTTP_PORT="${HTTP_PORT:-80}"
if command -v curl >/dev/null 2>&1; then
  curl -fsS "http://127.0.0.1:${HTTP_PORT}/health" >/dev/null && echo "health ok"
  curl -fsS "http://127.0.0.1:${HTTP_PORT}/api/ping" >/dev/null && echo "api ping ok"
else
  echo "curl not found, skip health check"
fi

echo "Done. Beta is on http://<ecs-ip>:${HTTP_PORT}"
