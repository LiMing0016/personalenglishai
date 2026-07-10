import assert from 'node:assert/strict'
import test from 'node:test'

import { parseCaptureTerms } from '../src/features/vocabulary/captureTerms'

test('parses newline comma and semicolon input without removing duplicate intent', () => {
  assert.deepEqual(parseCaptureTerms(' innovative, sustainable\nstate-of-the-art；innovative '), [
    'innovative',
    'sustainable',
    'state-of-the-art',
    'innovative',
  ])
})

test('caps one capture request at one hundred nonblank items', () => {
  const raw = Array.from({ length: 120 }, (_, index) => `word${index}`).join('\n')

  assert.equal(parseCaptureTerms(raw).length, 100)
})
