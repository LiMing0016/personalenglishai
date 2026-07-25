import assert from 'node:assert/strict'
import test from 'node:test'

import { isVocabularyGenerationActive } from '../src/features/vocabulary/generationPolling'

test('polls legacy generating cards and explicit pending or running jobs', () => {
  assert.equal(isVocabularyGenerationActive({ status: 'generating', generationStatus: null }), true)
  assert.equal(isVocabularyGenerationActive({ status: 'ready', generationStatus: 'pending' }), true)
  assert.equal(isVocabularyGenerationActive({ status: 'failed', generationStatus: 'running' }), true)
})

test('stops polling terminal generation jobs', () => {
  for (const generationStatus of ['succeeded', 'failed', 'cancelled']) {
    assert.equal(isVocabularyGenerationActive({ status: 'ready', generationStatus }), false)
  }
  assert.equal(isVocabularyGenerationActive({ status: 'ready', generationStatus: null }), false)
})
