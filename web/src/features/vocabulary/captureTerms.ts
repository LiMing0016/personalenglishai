const MAX_CAPTURE_TERMS = 100

export function parseCaptureTerms(raw: string): string[] {
  return [...new Set(raw
    .split(/[\n,;，；]+/u)
    .map((term) => term.trim())
    .filter(Boolean)
  )].slice(0, MAX_CAPTURE_TERMS)
}

export function createClientRequestId(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `capture-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
