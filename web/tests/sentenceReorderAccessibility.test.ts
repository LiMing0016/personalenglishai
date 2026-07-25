import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../src/components/assistant/learning-blocks/sentence-reorder/SentenceReorderBlock.vue', import.meta.url),
  'utf8',
)

test('sentence reorder card exposes native keyboard and touch controls', () => {
  assert.match(source, /<button[\s\S]*?v-for="token in availableTokens"/)
  assert.match(source, /<button[\s\S]*?v-for="token in answerTokens"/)
  assert.match(source, /min-height:\s*40px/)
  assert.doesNotMatch(source, /draggable|dragstart|drop=/)
})

test('sentence reorder card announces feedback and supports exit', () => {
  assert.match(source, /role="status"/)
  assert.match(source, /aria-live="polite"/)
  assert.match(source, /send\(\{ type: 'EXIT' \}\)/)
})

test('sentence reorder card delegates lifecycle to the shared machine', () => {
  assert.match(source, /useMachine\(activityMachine\)/)
  assert.match(source, /type: 'START'/)
  assert.match(source, /type: 'SUBMIT_SUCCESS'/)
  assert.match(source, /type: 'SUBMIT_ERROR'/)
})
