export function summarizeText(value: string | null | undefined, limit = 80): string {
  const normalized = (value ?? '').replace(/\s+/g, ' ').trim()
  if (!normalized) return '-'
  if (normalized.length <= limit) return normalized
  return `${normalized.slice(0, limit).trimEnd()}...`
}

export function formatTokens(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '-'
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '-'
  return numeric.toLocaleString('en-US')
}

export function statusLabel(value: string | null | undefined): string {
  if (value === 'completed') return 'Completed'
  if (value === 'failed') return 'Failed'
  if (value === 'partial') return 'Partial'
  return value || '-'
}

export function formatJson(value: unknown): string {
  if (value === null || value === undefined || value === '') return '{}'
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  return JSON.stringify(value, null, 2)
}
