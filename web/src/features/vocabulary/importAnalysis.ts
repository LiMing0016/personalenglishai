import type { VocabularyImportAnalysisResponse } from '@/api/vocabulary'
import type { ImportCandidate } from '@/features/vocabulary/imageRecognition'

export type VocabularyImportSortMode = 'input' | 'alphabetical'
export type VocabularyImportInputChangeState = 'idle' | 'stale'

export interface ImportAnalysisRequest {
  requestId: number
  fingerprint: string
  signal: AbortSignal
}

export interface ImportAnalysisLifecycle {
  begin: (fingerprint: string) => ImportAnalysisRequest
  isCurrent: (
    requestId: number,
    responseFingerprint: string,
    requestFingerprint: string,
    currentFingerprint: string,
  ) => boolean
  invalidate: () => void
  deactivate: () => void
}

export async function calculateVocabularyImportFingerprint(
  text: string,
  file: File | null,
): Promise<string> {
  const normalized = text.replace(/\r\n?/gu, '\n').trim()
  const textBytes = new TextEncoder().encode(normalized)
  const imageBytes = file
    ? new Uint8Array(await file.arrayBuffer())
    : new Uint8Array()
  const payload = new Uint8Array(textBytes.length + 1 + imageBytes.length)
  payload.set(textBytes)
  payload.set(imageBytes, textBytes.length + 1)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', payload)
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

export function createImportAnalysisLifecycle({
  createAbortController = () => new AbortController(),
}: {
  createAbortController?: () => AbortController
} = {}): ImportAnalysisLifecycle {
  let latestRequestId = 0
  let controller: AbortController | null = null

  function invalidate() {
    latestRequestId += 1
    controller?.abort()
    controller = null
  }

  return {
    begin(fingerprint) {
      invalidate()
      controller = createAbortController()
      const requestId = ++latestRequestId
      return { requestId, fingerprint, signal: controller.signal }
    },
    isCurrent(requestId, responseFingerprint, requestFingerprint, currentFingerprint) {
      return requestId === latestRequestId
        && !controller?.signal.aborted
        && responseFingerprint === requestFingerprint
        && requestFingerprint === currentFingerprint
    },
    invalidate,
    deactivate: invalidate,
  }
}

export function isVocabularyImportAnalysisEnabled(
  configuredValue: unknown = (
    import.meta.env as { VITE_VOCABULARY_IMPORT_ANALYSIS_ENABLED?: string } | undefined
  )?.VITE_VOCABULARY_IMPORT_ANALYSIS_ENABLED,
): boolean {
  return configuredValue === 'true'
}

export function importAnalysisStateAfterInputChange(
  lastSuccessfulFingerprint: string,
): VocabularyImportInputChangeState {
  return lastSuccessfulFingerprint ? 'stale' : 'idle'
}

export function isImportAnalysisAbort(error: unknown): boolean {
  return (error instanceof DOMException && error.name === 'AbortError')
    || Boolean(
      error
      && typeof error === 'object'
      && 'code' in error
      && (error as { code?: unknown }).code === 'ERR_CANCELED',
    )
}

export function mapVocabularyImportAnalysisCandidates(
  response: VocabularyImportAnalysisResponse,
  fileName: string | null,
): ImportCandidate[] {
  const normalizedFileName = fileName ? normalizeImportFileName(fileName) : null
  const seenTerms = new Set<string>()

  return response.items.flatMap((item) => {
    const term = item.normalizedTerm.trim()
    const key = term.toLocaleLowerCase('en-US')
    if (!term || seenTerms.has(key)) return []
    seenTerms.add(key)

    const usesImageSource = Boolean(normalizedFileName && item.evidence !== 'text')
    return [{
      id: `${response.traceId}:${item.itemId}`,
      source: usesImageSource ? 'ocr_image' : 'manual',
      sourceBatchId: usesImageSource ? response.traceId : 'manual',
      observedText: item.observedText,
      normalizedTerm: term,
      term,
      status: item.status,
      resolution: item.status === 'accepted' ? 'accepted' : 'unresolved',
      selected: true,
      suggestions: item.suggestions.map((suggestion) => ({ ...suggestion })),
      contextText: item.contextText,
      evidence: item.evidence,
      ...(usesImageSource
        ? {
            recognition: {
              ...response.generation,
              traceId: response.traceId,
              fileName: normalizedFileName!,
            },
          }
        : {}),
    } satisfies ImportCandidate]
  })
}

export function sortImportCandidates(
  candidates: readonly ImportCandidate[],
  mode: VocabularyImportSortMode,
): ImportCandidate[] {
  const stable = candidates.map((candidate, index) => ({ candidate, index }))
  if (mode === 'alphabetical') {
    stable.sort((left, right) => (
      left.candidate.term.localeCompare(right.candidate.term, 'en', { sensitivity: 'base' })
      || left.index - right.index
    ))
  }
  return stable.map(({ candidate }) => candidate)
}

function normalizeImportFileName(fileName: string): string {
  const pathSegments = fileName.split(/[\\/]/u)
  const baseName = pathSegments[pathSegments.length - 1] ?? ''
  const cleaned = baseName.replace(/[\u0000-\u001f\u007f]/gu, '').trim() || 'image'
  if (cleaned.length <= 255) return cleaned

  const extension = cleaned.match(/(\.[A-Za-z0-9]{1,15})$/u)?.[1] ?? ''
  return `${cleaned.slice(0, 255 - extension.length)}${extension}`
}
