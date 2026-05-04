import test from 'node:test'
import assert from 'node:assert/strict'

import { shouldOpenAssistantDrawerForSelection } from './appSelectionToolbar.ts'

test('shouldOpenAssistantDrawerForSelection keeps drawer closed inside assistant page', () => {
  assert.equal(shouldOpenAssistantDrawerForSelection('/app/assistant'), false)
})

test('shouldOpenAssistantDrawerForSelection opens drawer when entering assistant from another app page', () => {
  assert.equal(shouldOpenAssistantDrawerForSelection('/app/writing'), true)
})
