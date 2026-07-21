import {
  submitVocabularyProductEvents,
  type VocabularyProductEventBatch,
  type VocabularyProductEventName,
  type VocabularyProductEventProperty,
  type VocabularyImageRecognitionResponse,
} from '@/api/vocabulary'

const SESSION_STORAGE_KEY = 'vocabulary.productEventSessionId'

type SessionStorageLike = Pick<Storage, 'getItem' | 'setItem'>
type ProductEventSender = (batch: VocabularyProductEventBatch) => Promise<unknown>

interface ProductEventTrackerDependencies {
  storage?: SessionStorageLike | null
  createId?: () => string
  now?: () => number
  sendBatch?: ProductEventSender
}

interface RecognitionCompletedInput {
  outcome: 'success' | 'failed'
  traceId?: string
  candidateCount?: number
  suspectedCount?: number
  provider?: string
  model?: string
  promptVersion?: string
  modelCallCount?: number
  warningCodes?: string[]
}

interface CandidateConfirmationInput {
  traceId: string
  candidateCount: number
  suspectedCount: number
  selectedCount: number
  editedCount: number
  removedCount: number
  resolutionCount: number
}

export interface VocabularyProductEventTracker {
  readonly sessionId: string
  beginImageRecognition: () => {
    completed: (input: RecognitionCompletedInput) => Promise<void>
  }
  candidatesConfirmed: (input: CandidateConfirmationInput) => Promise<void>
  learningStarted: (cardUid: string, sourceType?: string) => Promise<void>
}

export type { VocabularyProductEventBatch }

export function createVocabularyProductEventTracker(
  dependencies: ProductEventTrackerDependencies = {},
): VocabularyProductEventTracker {
  const createId = dependencies.createId ?? randomId
  const now = dependencies.now ?? Date.now
  const sendBatch = dependencies.sendBatch ?? submitVocabularyProductEvents
  const sessionId = resolveSessionId(dependencies.storage, createId)
  const learningCards = new Set<string>()

  async function emit({
    eventName,
    traceId,
    cardUid,
    properties,
  }: {
    eventName: VocabularyProductEventName
    traceId?: string
    cardUid?: string
    properties: Record<string, VocabularyProductEventProperty | undefined>
  }): Promise<void> {
    const safeProperties = Object.fromEntries(
      Object.entries(properties).filter((entry): entry is [string, VocabularyProductEventProperty] => (
        entry[1] !== undefined
      )),
    )
    const batch: VocabularyProductEventBatch = {
      events: [{
        eventUid: `vocabulary-event:${createId()}`,
        eventName,
        ...(traceId ? { traceId } : {}),
        sessionId,
        ...(cardUid ? { cardUid } : {}),
        occurredAt: new Date(now()).toISOString().slice(0, 23),
        properties: safeProperties,
      }],
    }
    try {
      await sendBatch(batch)
    } catch {
      // Analytics is intentionally best effort and must not affect product flows.
    }
  }

  return {
    sessionId,
    beginImageRecognition() {
      const startedAt = now()
      void emit({
        eventName: 'vocabulary_image_recognition_started',
        properties: { sourceType: 'ocr_image' },
      })
      return {
        completed: (input) => emit({
          eventName: 'vocabulary_image_recognition_completed',
          traceId: input.traceId,
          properties: {
            sourceType: 'ocr_image',
            durationMs: Math.max(0, now() - startedAt),
            candidateCount: input.candidateCount,
            suspectedCount: input.suspectedCount,
            provider: input.provider,
            model: input.model,
            promptVersion: input.promptVersion,
            modelCallCount: input.modelCallCount,
            warningCodes: input.warningCodes,
            outcome: input.outcome,
          },
        }),
      }
    },
    candidatesConfirmed(input) {
      return emit({
        eventName: 'vocabulary_image_candidates_confirmed',
        traceId: input.traceId,
        properties: {
          sourceType: 'ocr_image',
          candidateCount: input.candidateCount,
          suspectedCount: input.suspectedCount,
          selectedCount: input.selectedCount,
          editedCount: input.editedCount,
          removedCount: input.removedCount,
          resolutionCount: input.resolutionCount,
        },
      })
    },
    learningStarted(cardUid, sourceType) {
      const normalizedCardUid = cardUid.trim()
      if (!normalizedCardUid || learningCards.has(normalizedCardUid)) return Promise.resolve()
      learningCards.add(normalizedCardUid)
      return emit({
        eventName: 'vocabulary_learning_started',
        cardUid: normalizedCardUid,
        properties: { sourceType },
      })
    },
  }
}

function resolveSessionId(
  configuredStorage: SessionStorageLike | null | undefined,
  createId: () => string,
): string {
  const storage = configuredStorage === undefined ? safeSessionStorage() : configuredStorage
  try {
    const stored = storage?.getItem(SESSION_STORAGE_KEY)?.trim()
    if (stored) return stored
  } catch {
    // Fall through to the in-memory ID.
  }

  const sessionId = `vocabulary-session:${createId()}`
  try {
    storage?.setItem(SESSION_STORAGE_KEY, sessionId)
  } catch {
    // Storage can be blocked by browser privacy settings.
  }
  return sessionId
}

function safeSessionStorage(): SessionStorageLike | null {
  try {
    return globalThis.sessionStorage ?? null
  } catch {
    return null
  }
}

function randomId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID()
  const bytes = new Uint8Array(16)
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes)
    return [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('')
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

export const vocabularyProductEvents = createVocabularyProductEventTracker()

export async function executeTrackedImageRecognition(
  mutation: () => Promise<VocabularyImageRecognitionResponse>,
  tracker: VocabularyProductEventTracker = vocabularyProductEvents,
): Promise<VocabularyImageRecognitionResponse> {
  const recognitionEvent = tracker.beginImageRecognition()
  try {
    const response = await mutation()
    void recognitionEvent.completed({
      outcome: 'success',
      traceId: response.traceId,
      candidateCount: response.items.length,
      suspectedCount: response.items.filter((item) => item.status === 'suspected_typo').length,
      provider: response.generation.provider,
      model: response.generation.model,
      promptVersion: response.generation.promptVersion,
      modelCallCount: response.generation.modelCallCount,
      warningCodes: response.warnings,
    })
    return response
  } catch (error) {
    void recognitionEvent.completed({ outcome: 'failed' })
    throw error
  }
}
