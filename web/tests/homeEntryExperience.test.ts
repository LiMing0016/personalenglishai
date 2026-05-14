import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const homeSource = readFileSync(new URL('../src/pages/Home.vue', import.meta.url), 'utf8')

assert.ok(homeSource.includes('进入学习'), 'home should make the learner app the primary entry')
assert.ok(homeSource.includes('to="/app"'), 'home should route the primary entry to /app')
assert.ok(homeSource.includes('to="/login"'), 'home should keep the normal login entry')
assert.ok(homeSource.includes('to="/register"'), 'home should keep registration available')
assert.ok(homeSource.includes('管理员入口'), 'home should keep a low-priority admin entry')
assert.ok(homeSource.includes('to="/admin"'), 'home admin entry should route to /admin')
assert.ok(homeSource.includes('internal-link'), 'home admin entry should be visually lower priority')
assert.ok(!homeSource.includes('AI 控制端'), 'home should not expose the AI control surface as a public entry')
assert.ok(!homeSource.includes('to="/ops/agent"'), 'home should not link to the legacy ops agent shell')

console.log('home-entry-experience-ok')
