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

export function isImportAnalysisAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
    || Boolean(
      error
      && typeof error === 'object'
      && 'code' in error
      && (error as { code?: unknown }).code === 'ERR_CANCELED',
    )
}
