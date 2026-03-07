#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BETA_DIR="$ROOT_DIR/deploy/beta"
ENV_FILE="${1:-$BETA_DIR/.env.beta}"
COMPOSE_FILE="$BETA_DIR/docker-compose.beta.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] env file not found: $ENV_FILE"
  exit 1
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=80 backend

HTTP_PORT="$(grep -E '^BETA_HTTP_PORT=' "$ENV_FILE" | tail -n1 | cut -d'=' -f2 || true)"
HTTP_PORT="${HTTP_PORT:-80}"

echo "Try: curl -i http://127.0.0.1:${HTTP_PORT}/health"
echo "Try: curl -i http://127.0.0.1:${HTTP_PORT}/api/ping"
