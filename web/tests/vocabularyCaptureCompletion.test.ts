import assert from 'node:assert/strict'
import test from 'node:test'

import { isVocabularyCaptureComplete } from '../src/features/vocabulary/captureCompletion'

test('capture is complete only when no item was rejected', () => {
  assert.equal(isVocabularyCaptureComplete({ items: [
    { term: 'one', cardUid: 'card_1', action: 'created', status: 'generating' },
  ] }), true)
  assert.equal(isVocabularyCaptureComplete({ items: [
    { term: 'one', cardUid: 'card_1', action: 'created', status: 'generating' },
    { term: 'two', cardUid: null, action: 'rejected', status: 'failed' },
  ] }), false)
})
