#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.local.yml"
LOCAL_PORTS_TEMPLATE="$ROOT_DIR/local-ports.env.example"
LOCAL_PORTS_FILE="$ROOT_DIR/local-ports.env"

usage() {
  cat <<'EOF'
Usage:
  ./start-local.sh                         Start local Docker services with rebuild
  ./start-local.sh up [service...]         Start services with rebuild
  ./start-local.sh fast [service...]       Start services without rebuild
  ./start-local.sh check                   Check Docker, compose file, and ports
  ./start-local.sh restart backend         Restart backend only
  ./start-local.sh restart assistant       Restart Python assistant orchestrator
  ./start-local.sh restart web             Restart web only
  ./start-local.sh restart all             Restart all services
  ./start-local.sh logs [service]          Tail logs, optionally for one service
  ./start-local.sh status                  Show compose service status
  ./start-local.sh down                    Stop and remove local Docker services

Service aliases:
  assistant/python/orchestrator -> assistant-orchestrator
  context                         -> context-sidecar
  ocr/paddle                      -> paddle-ocr
EOF
}

log() {
  printf '[PEAI] %s\n' "$*"
}

warn() {
  printf '[WARN] %s\n' "$*" >&2
}

error() {
  printf '[ERROR] %s\n' "$*" >&2
}

load_config_file() {
  local file="$1"
  local label="$2"

  if [[ ! -f "$file" ]]; then
    return 0
  fi

  log "Loading $label port config: $file"
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    key="${key//$'\r'/}"
    value="${value//$'\r'/}"
    [[ -z "$key" || "${key:0:1}" == "#" ]] && continue
    if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      export "$key=$value"
    fi
  done < "$file"
}

derive_port() {
  local target="$1"
  local base_var="$2"
  local base_value="${!base_var:-}"
  local current_value="${!target:-}"
  local offset="${PORT_OFFSET:-0}"

  if [[ -n "$current_value" ]]; then
    return 0
  fi

  if [[ -z "$base_value" ]]; then
    error "Missing base port for $target. Set $target or $base_var in local-ports.env."
    exit 1
  fi

  if ! [[ "$base_value" =~ ^[0-9]+$ && "$offset" =~ ^[0-9]+$ ]]; then
    error "Invalid port expression for $target: base=$base_value, offset=$offset."
    exit 1
  fi

  export "$target=$((base_value + offset))"
}

resolve_ports() {
  export PORT_OFFSET="${PORT_OFFSET:-0}"
  export PYTHON_HOST="${PYTHON_HOST:-127.0.0.1}"

  derive_port BACKEND_PORT BACKEND_BASE_PORT
  derive_port WEB_PORT WEB_BASE_PORT
  derive_port PYTHON_PORT PYTHON_BASE_PORT
  derive_port DOCS_PORT DOCS_BASE_PORT

  export ASSISTANT_ORCHESTRATOR_PORT="$PYTHON_PORT"
  export APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:$WEB_PORT}"
}

compose() {
  (cd "$ROOT_DIR" && docker compose -f "$COMPOSE_FILE" "$@")
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    error "Missing required command: $command_name"
    exit 1
  fi
}

check_prerequisites() {
  require_command docker
  require_command nc

  if [[ ! -f "$COMPOSE_FILE" ]]; then
    error "Missing compose file: $COMPOSE_FILE"
    exit 1
  fi

  if ! docker compose version >/dev/null 2>&1; then
    error "Docker Compose is not available. Install Docker Desktop and enable the compose plugin."
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    error "Docker daemon is not running. Start Docker Desktop first."
    exit 1
  fi
}

service_running() {
  local service="$1"
  local container_id
  container_id="$(compose ps -q "$service" 2>/dev/null || true)"
  [[ -n "$container_id" ]] && docker inspect -f '{{.State.Running}}' "$container_id" 2>/dev/null | grep -q true
}

port_pid() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true
}

ensure_port_available() {
  local service="$1"
  local name="$2"
  local port="$3"
  local pid

  if service_running "$service"; then
    return 0
  fi

  pid="$(port_pid "$port")"
  if [[ -n "$pid" ]]; then
    error "$name port $port is already in use by PID $pid."
    error "Close that process or adjust local-ports.env before starting."
    exit 1
  fi
}

check_ports_available() {
  ensure_port_available backend Backend "$BACKEND_PORT"
  ensure_port_available web Web "$WEB_PORT"
  ensure_port_available assistant-orchestrator Python "$PYTHON_PORT"
  ensure_port_available docs Docs "$DOCS_PORT"
}

wait_for_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local seconds="$4"
  local deadline=$((SECONDS + seconds))

  log "Waiting for $name at $host:$port ..."
  while (( SECONDS < deadline )); do
    if nc -z "$host" "$port" >/dev/null 2>&1; then
      log "$name is reachable."
      return 0
    fi
    sleep 1
  done

  warn "$name did not accept TCP connections within ${seconds}s."
  return 1
}

service_alias() {
  case "$1" in
    all) printf '%s\n' all ;;
    assistant|python|orchestrator) printf '%s\n' assistant-orchestrator ;;
    context) printf '%s\n' context-sidecar ;;
    ocr|paddle) printf '%s\n' paddle-ocr ;;
    backend|web|docs|mysql|redis|context-sidecar|paddle-ocr|assistant-orchestrator)
      printf '%s\n' "$1"
      ;;
    *)
      error "Unknown service alias: $1"
      exit 1
      ;;
  esac
}

run_check() {
  check_prerequisites
  check_ports_available
  log "Local startup prerequisites look ready."
}

run_up() {
  check_prerequisites
  check_ports_available
  log "Starting local Docker services with rebuild..."
  compose up -d --build "$@"
  wait_for_tcp "Python" "$PYTHON_HOST" "$PYTHON_PORT" 60 || true
  wait_for_tcp "Backend" "127.0.0.1" "$BACKEND_PORT" 120 || true
  wait_for_tcp "Docs" "127.0.0.1" "$DOCS_PORT" 60 || true
  wait_for_tcp "Web" "127.0.0.1" "$WEB_PORT" 120 || true
  log "Frontend: http://127.0.0.1:$WEB_PORT"
  log "Backend:  http://127.0.0.1:$BACKEND_PORT"
  log "Python:   http://$PYTHON_HOST:$PYTHON_PORT"
  log "Docs:     http://127.0.0.1:$DOCS_PORT"
}

run_fast_up() {
  check_prerequisites
  check_ports_available
  log "Starting local Docker services without rebuild..."
  compose up -d "$@"
}

run_restart() {
  check_prerequisites
  if [[ "$#" -eq 0 || "${1:-}" == "all" ]]; then
    log "Restarting all local services..."
    compose restart
    return 0
  fi

  local services=()
  local service
  for service in "$@"; do
    services+=("$(service_alias "$service")")
  done
  log "Restarting ${services[*]}..."
  compose restart "${services[@]}"
}

run_logs() {
  check_prerequisites
  if [[ "$#" -eq 0 || "${1:-}" == "all" ]]; then
    compose logs -f
    return 0
  fi

  local services=()
  local service
  for service in "$@"; do
    services+=("$(service_alias "$service")")
  done
  compose logs -f "${services[@]}"
}

run_rebuild() {
  check_prerequisites
  if [[ "$#" -eq 0 || "${1:-}" == "all" ]]; then
    log "Rebuilding and starting all services..."
    compose up -d --build
    return 0
  fi

  local services=()
  local service
  for service in "$@"; do
    services+=("$(service_alias "$service")")
  done
  log "Rebuilding and starting ${services[*]}..."
  compose up -d --build "${services[@]}"
}

load_config_file "$LOCAL_PORTS_TEMPLATE" "template"
load_config_file "$LOCAL_PORTS_FILE" "local"
resolve_ports

command_name="${1:-up}"
if [[ "$#" -gt 0 ]]; then
  shift
fi

case "$command_name" in
  up|start)
    run_up "$@"
    ;;
  fast)
    run_fast_up "$@"
    ;;
  check|--check)
    run_check
    ;;
  restart)
    run_restart "$@"
    ;;
  rebuild)
    run_rebuild "$@"
    ;;
  logs)
    run_logs "$@"
    ;;
  status|ps)
    check_prerequisites
    compose ps
    ;;
  down|stop)
    check_prerequisites
    compose down
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage
    error "Unknown command: $command_name"
    exit 1
    ;;
esac
