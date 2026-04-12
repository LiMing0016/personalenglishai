import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildHandwritingImportText,
  buildHandwritingImportConfirmPayload,
  createHandwritingImportRunGate,
  isHandwritingImportDisabled,
  normalizeHandwritingText,
} from './handwritingImportHelpers.js'

test('buildHandwritingImportText supports replace and append', () => {
  assert.equal(buildHandwritingImportText('old text', 'new text', 'replace'), 'new text')
  assert.equal(
    buildHandwritingImportText('old text', 'new text', 'append'),
    'old text\n\nnew text',
  )
})

test('isHandwritingImportDisabled rejects empty recognized text', () => {
  assert.equal(isHandwritingImportDisabled('   '), true)
  assert.equal(isHandwritingImportDisabled('\n\r\n'), true)
  assert.equal(isHandwritingImportDisabled('Essay body'), false)
  assert.equal(normalizeHandwritingText('  \r\nEssay body  \r\n'), 'Essay body')
})

test('run gate ignores cancelled and stale results', () => {
  const gate = createHandwritingImportRunGate()
  const first = gate.start()
  gate.cancel(first)
  const second = gate.start()

  assert.equal(gate.canApply(first), false)
  assert.equal(gate.isCurrent(first), false)
  assert.equal(gate.canApply(second), true)
  assert.equal(gate.isCurrent(second), true)
})

test('buildHandwritingImportConfirmPayload preserves raw recognized text', () => {
  const payload = buildHandwritingImportConfirmPayload({
    sourceText: 'Existing body',
    recognizedText: 'Line 1\nLine 2',
    normalizedText: 'Line 1\n\nLine 2',
    imageUrl: 'data:image/png;base64,abc',
    mode: 'append',
  })

  assert.deepEqual(payload, {
    mode: 'append',
    sourceText: 'Existing body',
    importedText: 'Line 1\n\nLine 2',
    combinedText: 'Existing body\n\nLine 1\n\nLine 2',
    recognizedText: 'Line 1\nLine 2',
    normalizedText: 'Line 1\n\nLine 2',
    imageUrl: 'data:image/png;base64,abc',
  })
})
