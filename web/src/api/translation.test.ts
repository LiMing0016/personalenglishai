import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildTranslationDocumentImportFormData,
  resolveStableTranslationParseProvider,
} from './translation.ts'

test('buildTranslationDocumentImportFormData sends stable PaddleOCR provider for standard parse', () => {
  const file = new File(['%PDF'], 'sample.pdf', { type: 'application/pdf' })
  const formData = buildTranslationDocumentImportFormData(file, 'immersive', 'standard', 'paddle-ocr')

  assert.equal(formData.get('mode'), 'immersive')
  assert.equal(formData.get('parseMode'), 'standard')
  assert.equal(formData.get('parseProvider'), 'paddle-ocr')
  assert.equal((formData.get('file') as File).name, 'sample.pdf')
})

test('buildTranslationDocumentImportFormData preserves local PaddleOCR-VL provider for high quality parse', () => {
  const file = new File(['%PDF'], 'sample.pdf', { type: 'application/pdf' })
  const formData = buildTranslationDocumentImportFormData(file, 'immersive', 'high_quality', 'local-paddle-vl')

  assert.equal(formData.get('mode'), 'immersive')
  assert.equal(formData.get('parseMode'), 'high_quality')
  assert.equal(formData.get('parseProvider'), 'local-paddle-vl')
  assert.equal((formData.get('file') as File).name, 'sample.pdf')
})

test('resolveStableTranslationParseProvider allows both local parse providers', () => {
  assert.equal(resolveStableTranslationParseProvider('paddle-ocr'), 'paddle-ocr')
  assert.equal(resolveStableTranslationParseProvider('local-paddle-vl'), 'local-paddle-vl')
})
