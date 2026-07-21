import assert from 'node:assert/strict'
import test from 'node:test'

import { http } from '../src/api/http'
import { analyzeVocabularyImport } from '../src/api/vocabulary'
import {
  calculateVocabularyImportFingerprint,
  createImportAnalysisLifecycle,
} from '../src/features/vocabulary/importAnalysis'


globalThis.localStorage = {
  getItem: () => null,
  setItem: () => undefined,
  removeItem: () => undefined,
  clear: () => undefined,
  key: () => null,
  get length() { return 0 },
} as Storage

const originalAdapter = http.defaults.adapter

test.afterEach(() => {
  http.defaults.adapter = originalAdapter
})

test('normalizes line endings and trims text before SHA-256 hashing', async () => {
  const first = await calculateVocabularyImportFingerprint('  one\r\ntwo  ', null)
  const second = await calculateVocabularyImportFingerprint('one\ntwo', null)

  assert.equal(first, second)
  assert.match(first, /^[0-9a-f]{64}$/)
})

test('includes raw image bytes after the zero-byte separator', async () => {
  const noImage = await calculateVocabularyImportFingerprint('one', null)
  const zeroByteImage = await calculateVocabularyImportFingerprint(
    'one',
    new File([new Uint8Array([0])], 'zero.png', { type: 'image/png' }),
  )

  assert.notEqual(noImage, zeroByteImage)
})

test('invalidating input aborts the request and rejects a late response', () => {
  const lifecycle = createImportAnalysisLifecycle()
  const fingerprint = 'a'.repeat(64)
  const request = lifecycle.begin(fingerprint)

  lifecycle.invalidate()

  assert.equal(request.signal.aborted, true)
  assert.equal(
    lifecycle.isCurrent(request.requestId, fingerprint, fingerprint, fingerprint),
    false,
  )
})

test('only the latest request with matching response, start, and current fingerprints is accepted', () => {
  const lifecycle = createImportAnalysisLifecycle()
  const fingerprint = 'a'.repeat(64)
  const old = lifecycle.begin(fingerprint)
  const latest = lifecycle.begin(fingerprint)

  assert.equal(old.signal.aborted, true)
  assert.equal(lifecycle.isCurrent(old.requestId, fingerprint, fingerprint, fingerprint), false)
  assert.equal(lifecycle.isCurrent(latest.requestId, fingerprint, fingerprint, fingerprint), true)
  assert.equal(lifecycle.isCurrent(latest.requestId, 'b'.repeat(64), fingerprint, fingerprint), false)
})

test('posts unified multipart input with caller signal and 60 second timeout', async () => {
  const signal = new AbortController().signal
  const file = new File(['image'], 'words.png', { type: 'image/png' })
  const fingerprint = 'a'.repeat(64)

  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'post')
    assert.equal(config.url, '/vocabulary/import-analyses')
    assert.equal(config.timeout, 60_000)
    assert.equal(config.signal, signal)
    assert.ok(config.data instanceof FormData)
    assert.deepEqual([...config.data.keys()], ['text', 'inputFingerprint', 'file'])
    assert.equal(config.data.get('text'), 'package')
    assert.equal(config.data.get('inputFingerprint'), fingerprint)
    assert.equal(config.data.get('file'), file)
    return {
      config,
      data: {
        code: '200000',
        data: {
          contractVersion: 1,
          traceId: 'trace-1',
          inputFingerprint: fingerprint,
          rawText: 'package',
          warnings: [],
          items: [],
          generation: {
            provider: 'openai',
            model: 'test-model',
            promptVersion: 'vocabulary-import-analysis-v1',
            modelCallCount: 1,
            traceId: 'trace-1',
            usage: null,
          },
        },
      },
      headers: {},
      status: 200,
      statusText: 'OK',
    }
  }

  const response = await analyzeVocabularyImport({
    text: 'package',
    file,
    inputFingerprint: fingerprint,
    signal,
  })
  assert.equal(response.inputFingerprint, fingerprint)
})
