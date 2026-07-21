import assert from 'node:assert/strict'
import test from 'node:test'

import {
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

test('keeps one vocabulary product event session ID in session storage', () => {
  const storage = new MemoryStorage()
  const ids = ['session-random', 'unused-random']

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

  assert.equal(first.sessionId, 'vocabulary-session:session-random')
  assert.equal(second.sessionId, first.sessionId)
})

test('falls back to a stable in-memory session when session storage is unavailable', () => {
  const tracker = createVocabularyProductEventTracker({
    storage: {
      getItem: () => { throw new Error('blocked') },
      setItem: () => { throw new Error('blocked') },
    },
    createId: () => 'fallback-random',
    sendBatch: async () => undefined,
  })

  assert.equal(tracker.sessionId, 'vocabulary-session:fallback-random')
  assert.equal(tracker.sessionId, 'vocabulary-session:fallback-random')
})

test('creates a random event UID for every emitted event', async () => {
  const requests: VocabularyProductEventBatch[] = []
  const ids = ['session', 'event-a', 'event-b']
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
    ['vocabulary-event:event-a', 'vocabulary-event:event-b'],
  )
})

test('recognition events contain only allowlisted aggregate properties and no private values', async () => {
  const requests: VocabularyProductEventBatch[] = []
  let now = 10_000
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: (() => { let sequence = 0; return () => `id-${sequence++}` })(),
    now: () => now,
    sendBatch: async (request) => { requests.push(request) },
  })

  const recognition = tracker.beginImageRecognition()
  now = 10_275
  await recognition.completed({
    outcome: 'success',
    traceId: 'trace-safe',
    candidateCount: 3,
    suspectedCount: 1,
    provider: 'openai',
    model: 'vision-model',
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
    createId: (() => { let sequence = 0; return () => `id-${sequence++}` })(),
    now: () => 20_000,
    sendBatch: async (request) => { requests.push(request) },
  })

  await tracker.candidatesConfirmed({
    traceId: 'trace-safe',
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
    createId: (() => { let sequence = 0; return () => `id-${sequence++}` })(),
    now: () => 30_000,
    sendBatch: async (request) => { requests.push(request) },
  })

  await tracker.learningStarted('card_1')
  await tracker.learningStarted('card_1')
  await tracker.learningStarted('card_2')

  assert.deepEqual(
    requests.map((request) => request.events[0]?.cardUid),
    ['card_1', 'card_2'],
  )
})

test('product event delivery is best effort and never rejects the product flow', async () => {
  const tracker = createVocabularyProductEventTracker({
    storage: new MemoryStorage(),
    createId: (() => { let sequence = 0; return () => `id-${sequence++}` })(),
    sendBatch: async () => { throw new Error('analytics unavailable') },
  })

  const recognition = tracker.beginImageRecognition()
  await assert.doesNotReject(recognition.completed({ outcome: 'failed' }))
  await assert.doesNotReject(tracker.candidatesConfirmed({
    traceId: 'trace-safe',
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
    createId: (() => { let sequence = 0; return () => `id-${sequence++}` })(),
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
    traceId: 'trace-safe',
    warnings: [],
    items: [],
    generation: {
      provider: 'openai', model: 'vision', promptVersion: 'v1', modelCallCount: 1,
    },
  } as VocabularyImageRecognitionResponse
  assert.equal(await executeTrackedImageRecognition(async () => response, tracker), response)
})
