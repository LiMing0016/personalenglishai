import { readFileSync } from 'node:fs'
import test from 'node:test'
import assert from 'node:assert/strict'

test('admin dev login bridge is dev-only and writes the auth token', () => {
  const router = readFileSync('web/src/router/index.ts', 'utf8')
  const bridge = readFileSync('web/src/pages/dev/AdminDevLoginBridge.vue', 'utf8')

  assert.match(router, /import\.meta\.env\.DEV/)
  assert.match(router, /\/dev\/admin-login/)
  assert.match(bridge, /localStorage\.setItem\('auth_token'/)
  assert.match(bridge, /router\.replace/)
})
