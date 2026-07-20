import type {
  VocabularyCaptureRequest,
  VocabularyImageRecognitionResponse,
  VocabularyImageRecognitionSuggestion,
  VocabularyRecognitionStatus,
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

export function mergeRecognitionCandidates(
  candidates: readonly ImportCandidate[],
  response: VocabularyImageRecognitionResponse,
  fileName: string,
): ImportCandidate[] {
  const merged = [...candidates]
  const existingTerms = new Set(candidates.map((candidate) => termKey(candidate.term)))

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
        fileName,
      },
    })
  }

  return merged
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
