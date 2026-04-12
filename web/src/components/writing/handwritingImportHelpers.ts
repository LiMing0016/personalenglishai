export type HandwritingImportStrategy = 'replace' | 'append'

export interface HandwritingImportPreview {
  strategy: HandwritingImportStrategy
  sourceText: string
  importedText: string
  combinedText: string
}

export interface HandwritingImportConfirmPayload {
  mode: HandwritingImportStrategy
  sourceText: string
  importedText: string
  combinedText: string
  recognizedText: string
  normalizedText: string
  imageUrl: string | null
}

export interface BuildHandwritingImportConfirmPayloadOptions {
  sourceText: string | null | undefined
  recognizedText: string | null | undefined
  normalizedText: string | null | undefined
  imageUrl: string | null | undefined
  mode: HandwritingImportStrategy
}

export interface HandwritingImportRunGate {
  start(): number
  cancel(runId?: number): void
  canApply(runId: number): boolean
  isCurrent(runId: number): boolean
  reset(): void
}

export function normalizeHandwritingText(text: string | null | undefined): string {
  return (text ?? '').replace(/\r\n?/g, '\n').trim()
}

export function isHandwritingImportDisabled(text: string | null | undefined): boolean {
  return normalizeHandwritingText(text).length === 0
}

function normalizeHandwritingSourceText(text: string | null | undefined): string {
  return (text ?? '').replace(/\r\n?/g, '\n')
}

export function buildHandwritingImportText(
  sourceText: string | null | undefined,
  importedText: string | null | undefined,
  strategy: HandwritingImportStrategy,
): string {
  const normalizedSource = normalizeHandwritingSourceText(sourceText)
  const normalizedImported = normalizeHandwritingText(importedText)

  if (strategy === 'replace') {
    return normalizedImported
  }

  if (!normalizedSource) {
    return normalizedImported
  }
  if (!normalizedImported) {
    return normalizedSource
  }
  const separator = normalizedSource.endsWith('\n') ? '' : '\n\n'
  return `${normalizedSource}${separator}${normalizedImported}`
}

export function createHandwritingImportRunGate(): HandwritingImportRunGate {
  let currentRunId = 0
  let cancelledBeforeRunId = 0

  const canApply = (runId: number) =>
    runId === currentRunId && runId > cancelledBeforeRunId

  return {
    start() {
      currentRunId += 1
      return currentRunId
    },
    cancel(runId = currentRunId) {
      cancelledBeforeRunId = Math.max(cancelledBeforeRunId, runId)
    },
    canApply,
    isCurrent(runId: number) {
      return canApply(runId)
    },
    reset() {
      currentRunId = 0
      cancelledBeforeRunId = 0
    },
  }
}

export function formatHandwritingConfidence(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return ''
  }
  const normalized = value > 1 ? value : value * 100
  return `${normalized.toFixed(normalized >= 10 ? 0 : 1)}%`
}

export function createHandwritingImportPreview(
  sourceText: string | null | undefined,
  importedText: string | null | undefined,
  strategy: HandwritingImportStrategy,
): HandwritingImportPreview {
  const source = normalizeHandwritingSourceText(sourceText)
  const imported = normalizeHandwritingText(importedText)
  return {
    strategy,
    sourceText: source,
    importedText: imported,
    combinedText: buildHandwritingImportText(source, imported, strategy),
  }
}

export function buildHandwritingImportConfirmPayload(
  options: BuildHandwritingImportConfirmPayloadOptions,
): HandwritingImportConfirmPayload {
  const sourceText = normalizeHandwritingSourceText(options.sourceText)
  const recognizedText = normalizeHandwritingText(options.recognizedText)
  const normalizedText = normalizeHandwritingText(options.normalizedText)

  return {
    mode: options.mode,
    sourceText,
    importedText: normalizedText,
    combinedText: buildHandwritingImportText(sourceText, normalizedText, options.mode),
    recognizedText,
    normalizedText,
    imageUrl: options.imageUrl?.trim() || null,
  }
}
