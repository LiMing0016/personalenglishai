import assert from 'node:assert/strict'
import test from 'node:test'

import { http } from '../src/api/http'
import { analyzeVocabularyImport } from '../src/api/vocabulary'
import {
  calculateVocabularyImportFingerprint,
  createImportAnalysisLifecycle,
  importAnalysisStateAfterInputChange,
  mapVocabularyImportAnalysisCandidates,
  sortImportCandidates,
  vocabularyImportAnalysisErrorMessage,
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

test('input changes leave a first request idle and make previously successful candidates stale', () => {
  assert.equal(importAnalysisStateAfterInputChange(''), 'idle')
  assert.equal(importAnalysisStateAfterInputChange('a'.repeat(64)), 'stale')
})

test('reports an unavailable AI analysis service instead of a generic failure for HTTP 503', () => {
  assert.equal(
    vocabularyImportAnalysisErrorMessage({ response: { status: 503 } }),
    'AI 分析服务暂不可用，请稍后重试',
  )
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

test('maps text and image evidence to capture candidates without losing generation metadata', () => {
  const response = {
    contractVersion: 1 as const,
    traceId: 'trace-1',
    inputFingerprint: 'a'.repeat(64),
    rawText: 'package',
    warnings: [],
    items: [
      {
        itemId: 'item-1',
        observedText: 'package',
        normalizedTerm: 'package',
        status: 'accepted' as const,
        suggestions: [],
        contextText: null,
        confidence: 0.99,
        evidence: 'text' as const,
      },
      {
        itemId: 'item-2',
        observedText: 'scrutinize',
        normalizedTerm: 'scrutinize',
        status: 'accepted' as const,
        suggestions: [],
        contextText: 'reading note',
        confidence: 0.97,
        evidence: 'image' as const,
      },
    ],
    generation: {
      provider: 'openai',
      model: 'test-model',
      promptVersion: 'vocabulary-import-analysis-v1',
      modelCallCount: 1,
      traceId: 'trace-1',
      usage: null,
    },
  }

  const candidates = mapVocabularyImportAnalysisCandidates(response, 'words.png')

  assert.equal(candidates[0]?.source, 'manual')
  assert.equal(candidates[1]?.source, 'ocr_image')
  assert.equal(candidates[1]?.recognition?.fileName, 'words.png')
  assert.equal(candidates[1]?.recognition?.promptVersion, 'vocabulary-import-analysis-v1')
})

test('sorts candidates alphabetically without mutating stable input order', () => {
  const candidates = mapVocabularyImportAnalysisCandidates({
    contractVersion: 1,
    traceId: 'trace-1',
    inputFingerprint: 'a'.repeat(64),
    rawText: '',
    warnings: [],
    items: ['supposed', 'package', 'Scrutinize'].map((term, index) => ({
      itemId: `item-${index}`,
      observedText: term,
      normalizedTerm: term,
      status: 'accepted' as const,
      suggestions: [],
      contextText: null,
      confidence: 0.99,
      evidence: 'text' as const,
    })),
    generation: {
      provider: 'openai',
      model: 'test-model',
      promptVersion: 'vocabulary-import-analysis-v1',
      modelCallCount: 1,
      traceId: 'trace-1',
      usage: null,
    },
  }, null)

  assert.deepEqual(sortImportCandidates(candidates, 'alphabetical').map((item) => item.term), [
    'package',
    'Scrutinize',
    'supposed',
  ])
  assert.deepEqual(candidates.map((item) => item.term), ['supposed', 'package', 'Scrutinize'])
  assert.deepEqual(sortImportCandidates(candidates, 'input').map((item) => item.term), [
    'supposed',
    'package',
    'Scrutinize',
  ])
})
