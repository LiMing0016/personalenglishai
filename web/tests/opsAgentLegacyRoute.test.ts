import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const routerSource = readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')

assert.ok(
  routerSource.includes("path: '/agent/agent-observability-center'"),
  'router should keep the legacy Agent observability URL addressable',
)
assert.ok(
  routerSource.includes('redirect: OPS_AGENT_HOME'),
  'legacy Agent observability URL should redirect to the current ops Agent home',
)
