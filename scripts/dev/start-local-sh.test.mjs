import assert from 'node:assert/strict'
import { readFileSync, statSync } from 'node:fs'

const rootScript = readFileSync(new URL('../../start-local.sh', import.meta.url), 'utf8')
const implementation = readFileSync(new URL('./start-local.sh', import.meta.url), 'utf8')

assert.ok(rootScript.includes('scripts/dev/start-local.sh'), 'root start-local.sh should delegate to scripts/dev/start-local.sh')
assert.ok(rootScript.includes('exec "$SCRIPT_DIR/scripts/dev/start-local.sh" "$@"'), 'root script should pass through all arguments')

assert.ok(implementation.startsWith('#!/usr/bin/env bash'), 'Mac script should be a bash script')
assert.ok(implementation.includes('set -euo pipefail'), 'Mac script should fail on unsafe shell errors')
assert.ok(implementation.includes('docker compose'), 'Mac script should use Docker Compose')
assert.ok(implementation.includes('docker-compose.local.yml'), 'Mac script should use the local compose file')
assert.ok(implementation.includes('local-ports.env.example'), 'Mac script should load the shared port template')
assert.ok(implementation.includes('local-ports.env'), 'Mac script should load local port overrides')
assert.ok(implementation.includes('ASSISTANT_ORCHESTRATOR_PORT="$PYTHON_PORT"'), 'Mac script should map PYTHON_PORT to compose assistant port')
assert.ok(implementation.includes('up -d --build'), 'default startup should rebuild local images before starting')
assert.ok(implementation.includes('wait_for_tcp "Backend"'), 'startup should wait for backend readiness')
assert.ok(implementation.includes('wait_for_tcp "Python"'), 'startup should wait for Python orchestrator readiness')
assert.ok(implementation.includes('restart backend'), 'help should document backend restart')
assert.ok(implementation.includes('restart assistant'), 'help should document assistant restart')
assert.ok(implementation.includes('logs [service]'), 'help should document log tailing')
assert.ok(implementation.includes('status'), 'help should document status')
assert.ok(implementation.includes('down'), 'help should document shutdown')
assert.ok(implementation.includes('assistant-orchestrator'), 'Mac script should map assistant alias to assistant-orchestrator service')
assert.ok(implementation.includes('paddle-ocr'), 'Mac script should support the OCR service alias')
assert.ok(!implementation.includes('mapfile'), 'Mac script should stay compatible with macOS default Bash 3.2')
assert.ok(!implementation.includes('mvnw.cmd'), 'Mac script should not use Windows Maven wrapper')
assert.ok(!implementation.includes('powershell'), 'Mac script should not depend on PowerShell')

const executableMask = 0o111
assert.ok((statSync(new URL('../../start-local.sh', import.meta.url)).mode & executableMask) !== 0, 'root script should be executable')
assert.ok((statSync(new URL('./start-local.sh', import.meta.url)).mode & executableMask) !== 0, 'implementation script should be executable')

console.log('start-local-sh-ok')
