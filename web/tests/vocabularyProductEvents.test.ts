import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createVocabularyProductEventRandomId,
  createVocabularyProductEventTracker,
  executeTrackedImageRecognition,
  type VocabularyProductEventBatch,
} from '../src/features/vocabulary/productEvents'
import type { VocabularyImageRecognitionResponse } from '../src/api/vocabulary'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string) { return this.values.get(key) ?? null }
  setItem(key: string, value: string) { this.values.set(key, value) }
}

const HEX_32_A = '0123456789abcdef0123456789abcdef'
const HEX_32_B = 'fedcba9876543210fedcba9876543210'
const TRACE_ID = `vocab-image-${HEX_32_A}`
const CARD_UID_A = `card_${HEX_32_A}`
const CARD_UID_B = `card_${HEX_32_B}`

function sequentialHexIds() {
  let sequence = 0
  return () => (sequence++).toString(16).padStart(32, '0')
}

test('keeps one vocabulary product event session ID in session storage', () => {
  const storage = new MemoryStorage()
  const ids = [HEX_32_A, HEX_32_B]

  const first = createVocabularyProductEventTracker({
    storage,
    createId: () => ids.shift()!,
    sendBatch: async () => undefined,
  })
  const second = createVocabularyProductEventTracker({
    storage,
    createId: () => ids.shift()!,
    sendBatch: async () => undefined,
  })

  assert.equal(first.sessionId, `vocabulary-session:${HEX_32_A}`)
  assert.equal(second.sessionId, first.sessionId)
})

test('falls back to a stable in-memory session when session storage is unavailable', () => {
  const tracker = createVocabularyProductEventTracker({
    storage: {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') },
    },
    createId: () => HEX_32_A,
    sendBatch: async () => undefined,
  })

  assert.equal(tracker.sessionId, `vocabulary-session:${HEX_32_A}`)
  assert.equal(tracker.sessionId, `vocabulary-session:${HEX_32_A}`)
})

test('replaces a legacy session value that does not match the production ID format', () => {
  const storage = new MemoryStorage()
  storage.setItem('vocabulary.productEventSessionId', 'vocabulary-session:private.png')

  const tracker = createVocabularyProductEventTracker({
    storage,
    createId: () => HEX_32_A,
    sendBatch: async () => undefined,
  })

  assert.equal(tracker.sessionId, `vocabulary-session:${HEX_32_A}`)
})

test('random ID fallback is always a 32 character hexadecimal value', () => {
  assert.equal(
    createVocabularyProductEventRandomId(null, () => 0),
    '00000000000000000000000000000000',
  )
})

test('creates a random event UID for every emitted event', async () => {
  const requests: VocabularyProductEventBatch[] = []
  const ids = [HEX_32_A, HEX_32_A, HEX_32_B]
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: () => ids.shift()!,
    now: () => 1_000,
    sendBatch: async (request) => { requests.push(request) },
  })

  const recognition = tracker.beginImageRecognition()
  await recognition.completed({ outcome: 'failed' })

  assert.deepEqual(
    requests.map((request) => request.events[0]?.eventUid),
    [`vocabulary-event:${HEX_32_A}`, `vocabulary-event:${HEX_32_B}`],
  )
})

test('recognition events contain only allowlisted aggregate properties and no private values', async () => {
  const requests: VocabularyProductEventBatch[] = []
  let now = 10_000
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: sequentialHexIds(),
    now: () => now,
    sendBatch: async (request) => { requests.push(request) },
  })

  const recognition = tracker.beginImageRecognition()
  now = 10_275
  await recognition.completed({
    outcome: 'success',
    traceId: TRACE_ID,
    candidateCount: 3,
    suspectedCount: 1,
    provider: 'openai',
    model: 'openai/gpt-4.1-mini',
    promptVersion: 'vocabulary-image-recognition-v1',
    modelCallCount: 1,
    warningCodes: ['CANDIDATE_LIMIT_REACHED'],
  })

  const completed = requests[1]!.events[0]!
  assert.equal(completed.properties.durationMs, 275)
  assert.deepEqual(Object.keys(completed.properties).sort(), [
    'candidateCount', 'durationMs', 'model', 'modelCallCount', 'outcome',
    'promptVersion', 'provider', 'sourceType', 'suspectedCount', 'warningCodes',
  ])
  const propertyKeys = requests.flatMap((request) => request.events.flatMap(
    (event) => Object.keys(event.properties),
  ))
  for (const forbidden of [
    'fileName', 'term', 'observedText', 'contextText', 'rawText',
    'content', 'markdown', 'image', 'base64',
  ]) {
    assert.equal(propertyKeys.some((key) => key.toLowerCase() === forbidden.toLowerCase()), false)
  }
})

test('records aggregate candidate confirmation counts without candidate text', async () => {
  const requests: VocabularyProductEventBatch[] = []
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: sequentialHexIds(),
    now: () => 20_000,
    sendBatch: async (request) => { requests.push(request) },
  })

  await tracker.candidatesConfirmed({
    traceId: TRACE_ID,
    candidateCount: 8,
    suspectedCount: 2,
    selectedCount: 5,
    editedCount: 1,
    removedCount: 3,
    resolutionCount: 2,
  })

  assert.deepEqual(requests[0]!.events[0]!.properties, {
    sourceType: 'ocr_image',
    candidateCount: 8,
    suspectedCount: 2,
    selectedCount: 5,
    editedCount: 1,
    removedCount: 3,
    resolutionCount: 2,
  })
})

test('records learning started only once per card in one page session', async () => {
  const requests: VocabularyProductEventBatch[] = []
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: sequentialHexIds(),
    now: () => 30_000,
    sendBatch: async (request) => { requests.push(request) },
  })

  await tracker.learningStarted(CARD_UID_A)
  await tracker.learningStarted(CARD_UID_A)
  await tracker.learningStarted(CARD_UID_B)

  assert.deepEqual(
    requests.map((request) => request.events[0]?.cardUid),
    [CARD_UID_A, CARD_UID_B],
  )
})

test('product event delivery is best effort and never rejects the product flow', async () => {
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: sequentialHexIds(),
    sendBatch: async () => { throw new Error('analytics unavailable') },
  })

  const recognition = tracker.beginImageRecognition()
  await assert.doesNotReject(recognition.completed({ outcome: 'failed' }))
  await assert.doesNotReject(tracker.candidatesConfirmed({
    traceId: TRACE_ID,
    candidateCount: 1,
    suspectedCount: 0,
    selectedCount: 1,
    editedCount: 0,
    removedCount: 0,
    resolutionCount: 0,
  }))
})

test('starts before the image mutation and completes even when the mutation is cancelled', async () => {
  const order: string[] = []
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: sequentialHexIds(),
    sendBatch: async (request) => {
      order.push(request.events[0]!.eventName)
    },
  })

  await assert.rejects(
    executeTrackedImageRecognition(async () => {
      order.push('mutation')
      throw Object.assign(new Error('cancelled'), { code: 'ERR_CANCELED' })
    }, tracker),
  )

  assert.deepEqual(order, [
    'vocabulary_image_recognition_started',
    'mutation',
    'vocabulary_image_recognition_completed',
  ])

  const response = {
    traceId: TRACE_ID,
    warnings: [],
    items: [],
    generation: {
      provider: 'openai', model: 'gpt-4.1-mini',
      promptVersion: 'vocabulary-image-recognition-v1', modelCallCount: 1,
    },
  } as VocabularyImageRecognitionResponse
  assert.equal(await executeTrackedImageRecognition(async () => response, tracker), response)
})
