import assert from 'node:assert/strict'
import test from 'node:test'

import { formatTokens, statusLabel, summarizeText } from './opsAgentView.ts'

test('summarizeText truncates long user input without losing short text', () => {
  assert.equal(summarizeText('short text', 20), 'short text')
  assert.equal(summarizeText('This is a very long assistant debug input.', 14), 'This is a very...')
})

test('formatTokens avoids NaN for missing values', () => {
  assert.equal(formatTokens(128), '128')
  assert.equal(formatTokens(null), '-')
  assert.equal(formatTokens(undefined), '-')
})

test('statusLabel maps known states and keeps unknown values readable', () => {
  assert.equal(statusLabel('completed'), 'Completed')
  assert.equal(statusLabel('failed'), 'Failed')
  assert.equal(statusLabel('partial'), 'Partial')
  assert.equal(statusLabel('custom_state'), 'custom_state')
})
