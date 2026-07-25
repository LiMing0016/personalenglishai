import assert from 'node:assert/strict'
import test from 'node:test'

import { parseCaptureTerms } from '../src/features/vocabulary/captureTerms'

test('parses newline comma and semicolon input with exact deduplication in first-seen order', () => {
  assert.deepEqual(parseCaptureTerms(' innovative, sustainable\nstate-of-the-art；innovative '), [
    'innovative',
    'sustainable',
    'state-of-the-art',
  ])
})

test('removes blank terms and caps one capture request at one hundred unique terms', () => {
  const raw = [
    '',
    '  ',
    'word0',
    'word0',
    ...Array.from({ length: 120 }, (_, index) => `word${index + 1}`),
  ].join('\n')

  assert.equal(parseCaptureTerms(raw).length, 100)
  assert.equal(parseCaptureTerms(raw)[0], 'word0')
  assert.equal(parseCaptureTerms(raw)[99], 'word99')
})
