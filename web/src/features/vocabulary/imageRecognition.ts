import type {
  VocabularyCaptureRequest,
  VocabularyImageRecognitionResponse,
  VocabularyImageRecognitionSuggestion,
  VocabularyRecognitionStatus,
  VocabularyRecognitionWarning,
} from '@/api/vocabulary'
import { createClientRequestId } from '@/features/vocabulary/captureTerms'

export const VOCABULARY_IMAGE_MAX_BYTES = 10 * 1024 * 1024
export const VOCABULARY_IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp'
const MAX_RECOGNITION_CANDIDATES = 30
const SUPPORTED_IMAGE_TYPES = new Set(VOCABULARY_IMAGE_ACCEPT.split(','))

export type CandidateResolution =
  | 'accepted'
  | 'unresolved'
  | 'suggestion_applied'
  | 'original_kept'

export interface ImportCandidate {
  id: string
  source: 'manual' | 'ocr_image'
  sourceBatchId: string
  observedText: string
  normalizedTerm: string
  term: string
  status: VocabularyRecognitionStatus
  resolution: CandidateResolution
  selected: boolean
  suggestions: VocabularyImageRecognitionSuggestion[]
  contextText: string | null
  recognition?: VocabularyImageRecognitionResponse['generation'] & {
    traceId: string
    fileName: string
  }
}

export interface CaptureBatch {
  candidateIds: string[]
  payload: VocabularyCaptureRequest
}

export interface RecognitionCandidateState {
  candidates: ImportCandidate[]
  warnings: VocabularyRecognitionWarning[]
}

export interface ImageRequestLifecycle {
  replacePreview: (file: File) => string
  beginRequest: () => { requestId: number, signal: AbortSignal }
  isLatest: (requestId: number) => boolean
  deactivate: () => void
  previewUrl: () => string
}

export class UnresolvedVocabularyCandidatesError extends Error {
  readonly code = 'UNRESOLVED_SELECTED_CANDIDATES'
  readonly candidateIds: string[]

  constructor(candidateIds: readonly string[]) {
    super('Selected vocabulary candidates require review')
    this.name = 'UnresolvedVocabularyCandidatesError'
    this.candidateIds = [...candidateIds]
  }
}

export function getVocabularyImageFileError(file: Pick<File, 'size' | 'type'>): string | null {
  if (!SUPPORTED_IMAGE_TYPES.has(file.type)) return '仅支持 JPG、PNG 或 WEBP 图片'
  if (file.size <= 0) return '图片不能为空'
  if (file.size > VOCABULARY_IMAGE_MAX_BYTES) return '图片不能超过 10 MB'
  return null
}

export function isVocabularyImageRecognitionEnabled(
  configuredValue: unknown = (import.meta.env as { VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED?: string } | undefined)
    ?.VITE_VOCABULARY_IMAGE_RECOGNITION_ENABLED,
): boolean {
  return configuredValue === 'true'
}

function termKey(term: string): string {
  return term.trim().toLocaleLowerCase('en-US')
}

export function reconcileManualCandidates(
  candidates: readonly ImportCandidate[],
  terms: readonly string[],
  createCandidateId: () => string,
): ImportCandidate[] {
  const imageCandidates = candidates.filter((candidate) => candidate.source === 'ocr_image')
  const imageTermKeys = new Set(imageCandidates.map((candidate) => termKey(candidate.term)))
  const existingManualByTerm = new Map<string, ImportCandidate>()
  for (const candidate of candidates) {
    if (candidate.source !== 'manual') continue
    const key = termKey(candidate.term)
    if (key && !imageTermKeys.has(key) && !existingManualByTerm.has(key)) {
      existingManualByTerm.set(key, candidate)
    }
  }

  const usedIds = new Set(imageCandidates.map((candidate) => candidate.id))
  const seenTerms = new Set<string>()
  const manualCandidates: ImportCandidate[] = []

  for (const rawTerm of terms) {
    const normalizedInput = rawTerm.trim()
    const key = termKey(normalizedInput)
    if (!key || imageTermKeys.has(key) || seenTerms.has(key)) continue
    seenTerms.add(key)

    const existing = existingManualByTerm.get(key)
    if (existing) {
      const id = uniqueCandidateId(existing.id, usedIds)
      usedIds.add(id)
      manualCandidates.push(id === existing.id ? existing : { ...existing, id })
      continue
    }

    const id = uniqueCandidateId(createCandidateId(), usedIds)
    usedIds.add(id)
    manualCandidates.push({
      id,
      source: 'manual',
      sourceBatchId: 'manual',
      observedText: normalizedInput,
      normalizedTerm: normalizedInput,
      term: normalizedInput,
      status: 'accepted',
      resolution: 'accepted',
      selected: true,
      suggestions: [],
      contextText: null,
    })
  }

  return [...manualCandidates, ...imageCandidates]
}

function uniqueCandidateId(preferredId: string, usedIds: ReadonlySet<string>): string {
  const baseId = preferredId.trim() || 'manual'
  if (!usedIds.has(baseId)) return baseId
  let suffix = 2
  while (usedIds.has(`${baseId}-${suffix}`)) suffix += 1
  return `${baseId}-${suffix}`
}

export function createImageRequestLifecycle({
  createObjectUrl = (file) => URL.createObjectURL(file),
  revokeObjectUrl = (url) => URL.revokeObjectURL(url),
  createAbortController = () => new AbortController(),
}: {
  createObjectUrl?: (file: File) => string
  revokeObjectUrl?: (url: string) => void
  createAbortController?: () => AbortController
} = {}): ImageRequestLifecycle {
  let currentPreviewUrl = ''
  let latestRequestId = 0
  let controller: AbortController | null = null

  function cancelRequest() {
    latestRequestId += 1
    controller?.abort()
    controller = null
  }

  function releasePreview() {
    if (currentPreviewUrl) revokeObjectUrl(currentPreviewUrl)
    currentPreviewUrl = ''
  }

  return {
    replacePreview(file) {
      cancelRequest()
      releasePreview()
      currentPreviewUrl = createObjectUrl(file)
      return currentPreviewUrl
    },
    beginRequest() {
      cancelRequest()
      controller = createAbortController()
      const requestId = ++latestRequestId
      return { requestId, signal: controller.signal }
    },
    isLatest(requestId) {
      return requestId === latestRequestId
    },
    deactivate() {
      cancelRequest()
      releasePreview()
    },
    previewUrl() {
      return currentPreviewUrl
    },
  }
}

export async function orchestrateCaptureBatches<
  TPayload,
  TResponse extends { items: unknown[] },
>({
  batches,
  capture,
  isComplete,
  onBatchComplete,
  onAllComplete,
}: {
  batches: ReadonlyArray<{ candidateIds: string[], payload: TPayload }>
  capture: (payload: TPayload) => Promise<TResponse>
  isComplete: (response: TResponse) => boolean
  onBatchComplete?: (candidateIds: string[], response: TResponse) => void
  onAllComplete?: (response: { items: TResponse['items'] }) => void
}): Promise<{ items: TResponse['items'], failed: boolean, error: unknown | null }> {
  const items = [] as TResponse['items']
  let failed = false
  let error: unknown | null = null

  for (const batch of batches) {
    try {
      const response = await capture(batch.payload)
      items.push(...response.items)
      if (isComplete(response)) onBatchComplete?.(batch.candidateIds, response)
      else failed = true
    } catch (captureError) {
      failed = true
      error = captureError
    }
  }

  if (!failed) onAllComplete?.({ items })
  return { items, failed, error }
}

export function mergeRecognitionCandidates(
  candidates: readonly ImportCandidate[],
  response: VocabularyImageRecognitionResponse,
  fileName: string,
): ImportCandidate[] {
  return mergeRecognitionCandidateState(candidates, response, fileName).candidates
}

export function mergeRecognitionCandidateState(
  candidates: readonly ImportCandidate[],
  response: VocabularyImageRecognitionResponse,
  fileName: string,
): RecognitionCandidateState {
  const merged = [...candidates]
  const existingTerms = new Set(candidates.map((candidate) => termKey(candidate.term)))
  const normalizedFileName = normalizeImageFileName(fileName)

  for (const item of response.items.slice(0, MAX_RECOGNITION_CANDIDATES)) {
    const key = termKey(item.normalizedTerm)
    if (!key || existingTerms.has(key)) continue
    existingTerms.add(key)
    merged.push({
      id: `${response.traceId}:${item.itemId}`,
      source: 'ocr_image',
      sourceBatchId: response.traceId,
      observedText: item.observedText,
      normalizedTerm: item.normalizedTerm.trim(),
      term: item.normalizedTerm.trim(),
      status: item.status,
      resolution: item.status === 'accepted' ? 'accepted' : 'unresolved',
      selected: true,
      suggestions: item.suggestions.map((suggestion) => ({ ...suggestion })),
      contextText: item.contextText,
      recognition: {
        ...response.generation,
        traceId: response.traceId,
        fileName: normalizedFileName,
      },
    })
  }

  return {
    candidates: merged,
    warnings: [...response.warnings],
  }
}

function normalizeImageFileName(fileName: string): string {
  const pathSegments = fileName.split(/[\\/]/u)
  const baseName = pathSegments[pathSegments.length - 1] ?? ''
  const cleaned = baseName.replace(/[\u0000-\u001f\u007f]/gu, '').trim() || 'image'
  if (cleaned.length <= 255) return cleaned

  const extension = cleaned.match(/(\.[A-Za-z0-9]{1,15})$/u)?.[1] ?? ''
  return `${cleaned.slice(0, 255 - extension.length)}${extension}`
}

function updateCandidate(
  candidates: readonly ImportCandidate[],
  candidateId: string,
  updater: (candidate: ImportCandidate) => ImportCandidate,
): ImportCandidate[] {
  return candidates.map((candidate) => candidate.id === candidateId ? updater(candidate) : candidate)
}

export function applySuggestion(
  candidates: readonly ImportCandidate[],
  candidateId: string,
  suggestion: string,
): ImportCandidate[] {
  return updateCandidate(candidates, candidateId, (candidate) => ({
    ...candidate,
    term: suggestion.trim(),
    resolution: candidate.source === 'manual' ? 'accepted' : 'suggestion_applied',
    selected: true,
  }))
}

export function keepOriginal(
  candidates: readonly ImportCandidate[],
  candidateId: string,
): ImportCandidate[] {
  return updateCandidate(candidates, candidateId, (candidate) => candidate.source === 'manual'
    ? candidate
    : {
        ...candidate,
        term: candidate.normalizedTerm,
        resolution: 'original_kept',
        selected: true,
      })
}

export function removeCandidate(
  candidates: readonly ImportCandidate[],
  candidateId: string,
): ImportCandidate[] {
  return candidates.filter((candidate) => candidate.id !== candidateId)
}

export function updateCandidateTerm(
  candidates: readonly ImportCandidate[],
  candidateId: string,
  term: string,
): ImportCandidate[] {
  const trimmedTerm = term.trim()
  return updateCandidate(candidates, candidateId, (candidate) => {
    if (candidate.source === 'manual') {
      return { ...candidate, term: trimmedTerm, resolution: 'accepted' }
    }
    const matchesRecognizedTerm = termKey(trimmedTerm) === termKey(candidate.normalizedTerm)
    return {
      ...candidate,
      term: trimmedTerm,
      resolution: matchesRecognizedTerm
        ? candidate.resolution === 'original_kept' ? 'original_kept' : 'accepted'
        : 'suggestion_applied',
    }
  })
}

export function selectedReadyCandidates(
  candidates: readonly ImportCandidate[],
): ImportCandidate[] {
  return candidates.filter((candidate) => (
    candidate.selected
    && candidate.resolution !== 'unresolved'
    && Boolean(candidate.term.trim())
  ))
}

export function selectAllReadyCandidates(candidates: readonly ImportCandidate[]): ImportCandidate[] {
  return candidates.map((candidate) => ({
    ...candidate,
    selected: candidate.resolution !== 'unresolved' && Boolean(candidate.term.trim()),
  }))
}

export function clearCandidateSelection(candidates: readonly ImportCandidate[]): ImportCandidate[] {
  return candidates.map((candidate) => ({ ...candidate, selected: false }))
}

export function buildCaptureBatches({
  candidates,
  themeUid,
  sourceContext,
  createRequestId = createClientRequestId,
}: {
  candidates: readonly ImportCandidate[]
  themeUid: string
  sourceContext: string
  createRequestId?: () => string
}): CaptureBatch[] {
  const unresolvedCandidateIds = candidates
    .filter((candidate) => candidate.selected && candidate.resolution === 'unresolved')
    .map((candidate) => candidate.id)
  if (unresolvedCandidateIds.length) {
    throw new UnresolvedVocabularyCandidatesError(unresolvedCandidateIds)
  }

  const groups = new Map<string, ImportCandidate[]>()
  for (const candidate of selectedReadyCandidates(candidates)) {
    const groupKey = candidate.source === 'manual' ? 'manual' : `ocr:${candidate.sourceBatchId}`
    const group = groups.get(groupKey)
    if (group) group.push(candidate)
    else groups.set(groupKey, [candidate])
  }

  const contextText = sourceContext.trim() || undefined
  return [...groups.values()].map((group) => {
    const first = group[0]!
    const source = first.source === 'manual'
      ? {
          type: 'manual' as const,
          sourceTitle: '手动录入',
          contextText,
          metadata: {},
        }
      : imageCaptureSource(first, contextText)

    return {
      candidateIds: group.map((candidate) => candidate.id),
      payload: {
        clientRequestId: createRequestId(),
        terms: group.map((candidate) => candidate.term.trim()),
        language: 'en',
        themeUid,
        source,
        ...(first.source === 'ocr_image'
          ? {
              itemSources: group.map((candidate) => ({
                ...(candidate.contextText?.trim()
                  ? { contextText: candidate.contextText.trim() }
                  : {}),
                metadata: {
                  observedText: candidate.observedText,
                  resolution: candidate.resolution,
                },
              })),
            }
          : {}),
      },
    }
  })
}

function imageCaptureSource(candidate: ImportCandidate, contextText?: string) {
  const recognition = candidate.recognition
  if (!recognition) throw new Error('OCR candidate is missing recognition metadata')
  return {
    type: 'ocr_image' as const,
    sourceRef: `recognition:${recognition.traceId}`,
    sourceTitle: '图片识别',
    contextText,
    metadata: {
      recognitionTraceId: recognition.traceId,
      fileName: recognition.fileName,
      provider: recognition.provider,
      model: recognition.model,
      promptVersion: recognition.promptVersion,
    },
  }
}
