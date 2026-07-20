import assert from 'node:assert/strict'
import test from 'node:test'

import * as imageRecognitionState from '../src/features/vocabulary/imageRecognition'
import { http } from '../src/api/http'
import {
  recognizeVocabularyImage,
  type VocabularyImageRecognitionResponse,
} from '../src/api/vocabulary'
import {
  VOCABULARY_IMAGE_MAX_BYTES,
  UnresolvedVocabularyCandidatesError,
  applySuggestion,
  buildCaptureBatches,
  clearCandidateSelection,
  getVocabularyImageFileError,
  isVocabularyImageRecognitionEnabled,
  keepOriginal,
  mergeRecognitionCandidateState,
  mergeRecognitionCandidates,
  removeCandidate,
  selectAllReadyCandidates,
  selectedReadyCandidates,
  updateCandidateTerm,
  type ImportCandidate,
} from '../src/features/vocabulary/imageRecognition'

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

function recognitionResponse(
  items: VocabularyImageRecognitionResponse['items'],
  warnings: VocabularyImageRecognitionResponse['warnings'] = [],
): VocabularyImageRecognitionResponse {
  return {
    contractVersion: 1,
    traceId: 'trace-1',
    rawText: 'recieve\npackage',
    warnings,
    items,
    generation: {
      provider: 'openai',
      model: 'vision-model',
      promptVersion: 'vocabulary-image-recognition-v1',
      modelCallCount: 1,
      traceId: 'trace-1',
      usage: { inputTokens: 40, outputTokens: 12 },
    },
  }
}

function recognitionItem(
  term: string,
  overrides: Partial<VocabularyImageRecognitionResponse['items'][number]> = {},
): VocabularyImageRecognitionResponse['items'][number] {
  return {
    itemId: `item-${term}`,
    observedText: term,
    normalizedTerm: term.trim().toLowerCase(),
    status: 'accepted',
    suggestions: [],
    contextText: null,
    confidence: 0.96,
    ...overrides,
  }
}

function manualCandidate(term: string, id = `manual-${term}`): ImportCandidate {
  return {
    id,
    source: 'manual',
    sourceBatchId: 'manual',
    observedText: term,
    normalizedTerm: term,
    term,
    status: 'accepted',
    resolution: 'accepted',
    selected: true,
    suggestions: [],
    contextText: null,
  }
}

function acceptedImageCandidate(
  term: string,
  traceId = 'trace-1',
  fileName = 'words.png',
): ImportCandidate {
  return mergeRecognitionCandidates(
    [],
    { ...recognitionResponse([recognitionItem(term)]), traceId },
    fileName,
  )[0]!
}

test('accepts only non-empty JPEG, PNG, and WEBP files up to 10 MB', () => {
  assert.equal(getVocabularyImageFileError({ type: 'image/jpeg', size: 1 }), null)
  assert.equal(getVocabularyImageFileError({ type: 'image/png', size: VOCABULARY_IMAGE_MAX_BYTES }), null)
  assert.equal(getVocabularyImageFileError({ type: 'image/webp', size: 10 }), null)
  assert.equal(getVocabularyImageFileError({ type: 'image/gif', size: 10 }), '仅支持 JPG、PNG 或 WEBP 图片')
  assert.equal(getVocabularyImageFileError({ type: 'image/png', size: 0 }), '图片不能为空')
  assert.equal(
    getVocabularyImageFileError({ type: 'image/png', size: VOCABULARY_IMAGE_MAX_BYTES + 1 }),
    '图片不能超过 10 MB',
  )
})

test('enables image recognition only for the exact string true', () => {
  assert.equal(isVocabularyImageRecognitionEnabled('true'), true)
  assert.equal(isVocabularyImageRecognitionEnabled('TRUE'), false)
  assert.equal(isVocabularyImageRecognitionEnabled(' true '), false)
  assert.equal(isVocabularyImageRecognitionEnabled(true), false)
  assert.equal(isVocabularyImageRecognitionEnabled(undefined), false)
})

test('posts only the image file as FormData with a 60 second timeout and caller signal', async () => {
  const signal = new AbortController().signal
  const file = new File(['image'], 'words.png', { type: 'image/png' })

  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'post')
    assert.equal(config.url, '/vocabulary/image-recognitions')
    assert.equal(config.timeout, 60_000)
    assert.equal(config.signal, signal)
    assert.ok(config.data instanceof FormData)
    assert.deepEqual([...config.data.keys()], ['file'])
    assert.equal(config.data.get('file'), file)
    assert.notEqual(config.headers.get('Content-Type'), 'multipart/form-data')
    return {
      config,
      data: { code: '200000', data: recognitionResponse([recognitionItem('package')]) },
      headers: {},
      status: 200,
      statusText: 'OK',
    }
  }

  const response = await recognizeVocabularyImage({ file, signal })
  assert.equal(response.items[0]?.normalizedTerm, 'package')
})

test('maps accepted and suspected typo items to deterministic default states', () => {
  const response = recognitionResponse([
    recognitionItem('package'),
    recognitionItem('recieve', {
      status: 'suspected_typo',
      suggestions: [
        { term: 'receive', dictionaryVerified: true },
        { term: 'receiver', dictionaryVerified: false },
      ],
    }),
  ])

  const candidates = mergeRecognitionCandidates([], response, 'notes.png')

  assert.deepEqual(candidates.map(({ term, resolution, selected }) => ({ term, resolution, selected })), [
    { term: 'package', resolution: 'accepted', selected: true },
    { term: 'recieve', resolution: 'unresolved', selected: true },
  ])
  assert.equal(candidates[1]?.suggestions[0]?.dictionaryVerified, true)
  assert.equal(candidates[1]?.suggestions[1]?.dictionaryVerified, false)
  assert.equal(candidates[0]?.recognition?.fileName, 'notes.png')
})

test('keeps first occurrence order while deduplicating candidates without case sensitivity', () => {
  const existing = [manualCandidate('Hello')]
  const response = recognitionResponse([
    recognitionItem('hello', { normalizedTerm: 'hello' }),
    recognitionItem('Package'),
    recognitionItem('package'),
    recognitionItem('WORLD'),
  ])

  const candidates = mergeRecognitionCandidates(existing, response, 'words.png')

  assert.deepEqual(candidates.map((candidate) => candidate.term), ['Hello', 'package', 'world'])
  assert.equal(candidates[0], existing[0])
})

test('limits each recognition result to 30 candidates and exposes the limit warning in merge state', () => {
  const response = recognitionResponse(
    Array.from({ length: 35 }, (_, index) => recognitionItem(`word-${index + 1}`)),
    ['CANDIDATE_LIMIT_REACHED'],
  )

  const result = mergeRecognitionCandidateState([], response, 'many.png')

  assert.equal(result.candidates.length, 30)
  assert.deepEqual(result.warnings, ['CANDIDATE_LIMIT_REACHED'])
  assert.equal(JSON.stringify(result).includes('rawText'), false)
})

test('requires an explicit decision before selected typo candidates become ready', () => {
  const [candidate] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', {
      status: 'suspected_typo',
      suggestions: [{ term: 'receive', dictionaryVerified: true }],
    }),
  ]), 'words.png')

  assert.deepEqual(selectedReadyCandidates([candidate!]), [])
  assert.equal(applySuggestion([candidate!], candidate!.id, 'receive')[0]?.resolution, 'suggestion_applied')
  assert.equal(keepOriginal([candidate!], candidate!.id)[0]?.resolution, 'original_kept')
})

test('applies suggestions, keeps originals, removes candidates, and never mutates inputs', () => {
  const [candidate] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', {
      status: 'suspected_typo',
      suggestions: [{ term: 'receive', dictionaryVerified: true }],
    }),
  ]), 'words.png')
  const initial = [candidate!]

  const applied = applySuggestion(initial, candidate!.id, 'receive')
  const kept = keepOriginal(initial, candidate!.id)
  const removed = removeCandidate(initial, candidate!.id)

  assert.notEqual(applied, initial)
  assert.notEqual(applied[0], initial[0])
  assert.equal(initial[0]?.term, 'recieve')
  assert.deepEqual({ term: applied[0]?.term, resolution: applied[0]?.resolution }, {
    term: 'receive',
    resolution: 'suggestion_applied',
  })
  assert.deepEqual({ term: kept[0]?.term, resolution: kept[0]?.resolution }, {
    term: 'recieve',
    resolution: 'original_kept',
  })
  assert.deepEqual(removed, [])
})

test('updates candidate terms with source-safe resolution semantics', () => {
  const manual = manualCandidate('hello')
  const accepted = acceptedImageCandidate('package')
  const [typo] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', { status: 'suspected_typo', suggestions: [{ term: 'receive', dictionaryVerified: true }] }),
  ]), 'words.png')

  const editedManual = updateCandidateTerm([manual], manual.id, 'greeting')[0]!
  const unchangedAccepted = updateCandidateTerm([accepted], accepted.id, ' package ')[0]!
  const editedAccepted = updateCandidateTerm([accepted], accepted.id, 'parcel')[0]!
  const explicitlyKept = keepOriginal([typo!], typo!.id)
  const keptAfterEdit = updateCandidateTerm(explicitlyKept, typo!.id, 'recieve')[0]!
  const correctedByTyping = updateCandidateTerm([typo!], typo!.id, 'receive')[0]!

  assert.equal(editedManual.source, 'manual')
  assert.equal(editedManual.resolution, 'accepted')
  assert.equal(unchangedAccepted.resolution, 'accepted')
  assert.equal(editedAccepted.resolution, 'suggestion_applied')
  assert.equal(keptAfterEdit.resolution, 'original_kept')
  assert.equal(correctedByTyping.resolution, 'suggestion_applied')
})

test('selects only resolved candidates and clears all selections immutably', () => {
  const ready = acceptedImageCandidate('package')
  const [unresolved] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', { status: 'suspected_typo', suggestions: [{ term: 'receive', dictionaryVerified: true }] }),
  ]), 'words.png')
  const initial = [
    { ...ready, selected: false },
    { ...unresolved!, selected: true },
  ]

  const selected = selectAllReadyCandidates(initial)
  const cleared = clearCandidateSelection(selected)

  assert.deepEqual(selected.map((candidate) => candidate.selected), [true, false])
  assert.deepEqual(cleared.map((candidate) => candidate.selected), [false, false])
  assert.deepEqual(initial.map((candidate) => candidate.selected), [false, true])
})

test('groups mixed candidates into source-safe capture batches', () => {
  const firstImage = acceptedImageCandidate('package', 'trace-1')
  const secondImage = acceptedImageCandidate('world', 'trace-2')
  const batches = buildCaptureBatches({
    candidates: [manualCandidate('hello'), firstImage, manualCandidate('welcome', 'manual-welcome'), secondImage],
    themeUid: 'theme_system_basic',
    sourceContext: 'chapter 2',
    createRequestId: (() => {
      let index = 0
      return () => `request-${++index}`
    })(),
  })

  assert.equal(batches.length, 3)
  assert.deepEqual(batches.map((batch) => batch.payload.clientRequestId), ['request-1', 'request-2', 'request-3'])
  assert.equal(batches[0]?.payload.source.type, 'manual')
  assert.deepEqual(batches[0]?.payload.terms, ['hello', 'welcome'])
  assert.equal(batches[0]?.payload.itemSources, undefined)
  assert.equal(batches[1]?.payload.source.type, 'ocr_image')
  assert.deepEqual(batches[1]?.payload.source.metadata, {
    recognitionTraceId: 'trace-1',
    fileName: 'words.png',
    provider: 'openai',
    model: 'vision-model',
    promptVersion: 'vocabulary-image-recognition-v1',
  })
  assert.deepEqual(batches[1]?.payload.itemSources?.[0], {
    metadata: {
      observedText: 'package',
      resolution: 'accepted',
    },
  })
  assert.equal(batches[2]?.payload.source.metadata.recognitionTraceId, 'trace-2')
  assert.equal(JSON.stringify(batches).includes('rawText'), false)
})

test('fails closed when any unresolved candidate is selected', () => {
  const accepted = acceptedImageCandidate('package')
  const [unresolved] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', {
      status: 'suspected_typo',
      suggestions: [{ term: 'receive', dictionaryVerified: true }],
    }),
  ]), 'words.png')
  let requestIdCalls = 0

  assert.throws(
    () => buildCaptureBatches({
      candidates: [accepted, unresolved!],
      themeUid: 'theme_system_basic',
      sourceContext: '',
      createRequestId: () => {
        requestIdCalls += 1
        return `request-${requestIdCalls}`
      },
    }),
    (error: unknown) => {
      assert.ok(error instanceof UnresolvedVocabularyCandidatesError)
      assert.equal(error.code, 'UNRESOLVED_SELECTED_CANDIDATES')
      assert.deepEqual(error.candidateIds, [unresolved!.id])
      return true
    },
  )
  assert.equal(requestIdCalls, 0)
})

test('does not let an unselected unresolved candidate block ready batches', () => {
  const accepted = acceptedImageCandidate('package')
  const [unresolved] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', {
      status: 'suspected_typo',
      suggestions: [{ term: 'receive', dictionaryVerified: true }],
    }),
  ]), 'words.png')

  const batches = buildCaptureBatches({
    candidates: [accepted, { ...unresolved!, selected: false }],
    themeUid: 'theme_system_basic',
    sourceContext: '',
    createRequestId: () => 'request-1',
  })

  assert.deepEqual(batches.map((batch) => batch.payload.terms), [['package']])
})

test('stores a path-free file name within 255 characters while preserving a reasonable extension', () => {
  const originalName = `C:\\private\\notes\\${'a'.repeat(300)}.png`
  const candidate = acceptedImageCandidate('package', 'trace-1', originalName)
  const batches = buildCaptureBatches({
    candidates: [candidate],
    themeUid: 'theme_system_basic',
    sourceContext: '',
    createRequestId: () => 'request-1',
  })
  const fileName = String(batches[0]?.payload.source.metadata.fileName)

  assert.equal(fileName.length, 255)
  assert.equal(fileName.endsWith('.png'), true)
  assert.equal(fileName.includes('private'), false)
  assert.equal(fileName.includes('\\'), false)
  assert.equal(fileName.includes('/'), false)
})

test('excludes unselected candidates while preserving per-item context', () => {
  const accepted = {
    ...acceptedImageCandidate('package'),
    contextText: 'I received a package.',
  }
  const unselected = { ...acceptedImageCandidate('world'), selected: false }
  const [unresolved] = mergeRecognitionCandidates([], recognitionResponse([
    recognitionItem('recieve', { status: 'suspected_typo', suggestions: [{ term: 'receive', dictionaryVerified: true }] }),
  ]), 'words.png')

  const batches = buildCaptureBatches({
    candidates: [accepted, unselected, { ...unresolved!, selected: false }],
    themeUid: 'theme_system_basic',
    sourceContext: '',
    createRequestId: () => 'request-1',
  })

  assert.equal(selectedReadyCandidates([accepted, unselected, { ...unresolved!, selected: false }]).length, 1)
  assert.deepEqual(batches[0]?.payload.itemSources?.[0], {
    contextText: 'I received a package.',
    metadata: { observedText: 'package', resolution: 'accepted' },
  })
})

test('reconciles manual candidates incrementally without changing OCR candidates', () => {
  const reconcile = (imageRecognitionState as Record<string, unknown>).reconcileManualCandidates
  assert.equal(typeof reconcile, 'function')

  const originalHello = { ...manualCandidate('Hello', 'manual-1'), selected: false }
  const duplicateHello = manualCandidate('hello', 'manual-duplicate')
  const imageCandidate = acceptedImageCandidate('Photo', 'trace-image')
  let nextId = 1
  const createId = () => `manual-new-${nextId++}`

  const first = (reconcile as (
    candidates: readonly ImportCandidate[],
    terms: readonly string[],
    createId: () => string,
  ) => ImportCandidate[])(
    [originalHello, duplicateHello, imageCandidate],
    ['hello', 'World', 'WORLD', 'photo'],
    createId,
  )

  assert.deepEqual(first.filter((candidate) => candidate.source === 'manual').map((candidate) => ({
    id: candidate.id,
    term: candidate.term,
    selected: candidate.selected,
  })), [
    { id: 'manual-1', term: 'Hello', selected: false },
    { id: 'manual-new-1', term: 'World', selected: true },
  ])
  assert.equal(first.filter((candidate) => candidate.id === 'manual-1').length, 1)
  assert.equal(new Set(first.map((candidate) => candidate.id)).size, first.length)
  assert.equal(first.find((candidate) => candidate.source === 'ocr_image'), imageCandidate)
  assert.equal(first.some((candidate) => candidate.source === 'manual' && candidate.term === 'photo'), false)

  const second = (reconcile as (
    candidates: readonly ImportCandidate[],
    terms: readonly string[],
    createId: () => string,
  ) => ImportCandidate[])(first, ['world'], createId)
  assert.deepEqual(second.filter((candidate) => candidate.source === 'manual').map((candidate) => ({
    id: candidate.id,
    selected: candidate.selected,
  })), [{ id: 'manual-new-1', selected: true }])
  assert.equal(second.find((candidate) => candidate.source === 'ocr_image'), imageCandidate)
})

test('image request lifecycle keeps only the latest request and releases previews on deactivation', () => {
  const createLifecycle = (imageRecognitionState as Record<string, unknown>).createImageRequestLifecycle
  assert.equal(typeof createLifecycle, 'function')

  const revoked: string[] = []
  const controllers: Array<{ signal: AbortSignal, abort: () => void, aborted: boolean }> = []
  let previewIndex = 0
  const lifecycle = (createLifecycle as (dependencies: {
    createObjectUrl: (file: File) => string
    revokeObjectUrl: (url: string) => void
    createAbortController: () => AbortController
  }) => {
    replacePreview: (file: File) => string
    beginRequest: () => { requestId: number, signal: AbortSignal }
    isLatest: (requestId: number) => boolean
    deactivate: () => void
    previewUrl: () => string
  })({
    createObjectUrl: () => `blob:preview-${++previewIndex}`,
    revokeObjectUrl: (url) => revoked.push(url),
    createAbortController: () => {
      const controller = {
        signal: {} as AbortSignal,
        aborted: false,
        abort() { this.aborted = true },
      }
      controllers.push(controller)
      return controller as AbortController
    },
  })

  assert.equal(lifecycle.replacePreview({ name: 'first.png' } as File), 'blob:preview-1')
  const first = lifecycle.beginRequest()
  const second = lifecycle.beginRequest()
  assert.equal(controllers[0]?.aborted, true)
  assert.equal(lifecycle.isLatest(first.requestId), false)
  assert.equal(lifecycle.isLatest(second.requestId), true)

  assert.equal(lifecycle.replacePreview({ name: 'second.png' } as File), 'blob:preview-2')
  assert.equal(controllers[1]?.aborted, true)
  assert.deepEqual(revoked, ['blob:preview-1'])
  const third = lifecycle.beginRequest()
  lifecycle.deactivate()
  assert.equal(controllers[2]?.aborted, true)
  assert.equal(lifecycle.isLatest(third.requestId), false)
  assert.equal(lifecycle.previewUrl(), '')
  assert.deepEqual(revoked, ['blob:preview-1', 'blob:preview-2'])
})

test('capture orchestration removes only complete batches and announces captured after every batch completes', async () => {
  const orchestrate = (imageRecognitionState as Record<string, unknown>).orchestrateCaptureBatches
  assert.equal(typeof orchestrate, 'function')

  const batches = [
    { candidateIds: ['a'], payload: { marker: 'a' } },
    { candidateIds: ['b'], payload: { marker: 'b' } },
    { candidateIds: ['c'], payload: { marker: 'c' } },
  ]
  const completed: string[][] = []
  const announced: string[] = []
  const firstResult = await (orchestrate as Function)({
    batches,
    capture: async (payload: { marker: string }) => {
      if (payload.marker === 'c') throw new Error('network failed')
      return {
        items: [{
          term: payload.marker,
          cardUid: payload.marker === 'a' ? 'card-a' : null,
          action: payload.marker === 'a' ? 'created' : 'rejected',
          status: payload.marker === 'a' ? 'generating' : 'failed',
        }],
      }
    },
    isComplete: (response: { items: Array<{ action: string }> }) => response.items.every((item) => item.action !== 'rejected'),
    onBatchComplete: (candidateIds: string[]) => completed.push(candidateIds),
    onAllComplete: () => announced.push('captured'),
  })

  assert.deepEqual(completed, [['a']])
  assert.deepEqual(announced, [])
  assert.equal(firstResult.failed, true)
  assert.deepEqual(firstResult.items.map((item: { term: string }) => item.term), ['a', 'b'])

  const order: string[] = []
  await (orchestrate as Function)({
    batches: batches.slice(0, 2),
    capture: async (payload: { marker: string }) => {
      order.push(`capture-${payload.marker}`)
      return { items: [{ term: payload.marker, cardUid: `card-${payload.marker}`, action: 'created', status: 'generating' }] }
    },
    isComplete: () => true,
    onBatchComplete: (candidateIds: string[]) => order.push(`remove-${candidateIds[0]}`),
    onAllComplete: () => order.push('captured'),
  })
  assert.deepEqual(order, ['capture-a', 'remove-a', 'capture-b', 'remove-b', 'captured'])
})
