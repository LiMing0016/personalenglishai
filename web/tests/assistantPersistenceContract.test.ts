import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantStateSource = readFileSync(new URL('../src/pages/app/assistantState.ts', import.meta.url), 'utf8')

assert.ok(assistantStateSource.includes("peai:assistant:state:v1"))
assert.ok(assistantStateSource.includes('restoreAssistantState'))
assert.ok(assistantStateSource.includes('persistState'))
assert.ok(assistantStateSource.includes('storage.setItem'))
assert.ok(assistantStateSource.includes("message.status !== 'loading'"))
