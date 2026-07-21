import type { SentenceReorderData, SentenceReorderItem } from '../contracts.ts'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function hasText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isExactTokenOrder(value: unknown, tokenIds: Set<string>) {
  if (!Array.isArray(value) || value.length !== tokenIds.size) return false
  if (!value.every(hasText)) return false
  return new Set(value).size === tokenIds.size && value.every((id) => tokenIds.has(id))
}

function isSentenceReorderItem(value: unknown): value is SentenceReorderItem {
  if (!isRecord(value) || !hasText(value.id) || !hasText(value.instruction)) return false
  if (!Array.isArray(value.tokens) || value.tokens.length < 2) return false

  const tokenIds = new Set<string>()
  for (const token of value.tokens) {
    if (!isRecord(token) || !hasText(token.id) || !hasText(token.text) || tokenIds.has(token.id)) {
      return false
    }
    tokenIds.add(token.id)
  }

  if (!isExactTokenOrder(value.initialOrder, tokenIds)) return false
  if (!Array.isArray(value.acceptedOrders) || value.acceptedOrders.length === 0) return false
  if (!value.acceptedOrders.every((order) => isExactTokenOrder(order, tokenIds))) return false
  if (value.translation !== undefined && !hasText(value.translation)) return false
  if (value.explanation !== undefined && !hasText(value.explanation)) return false
  if (value.hint !== undefined && !hasText(value.hint)) return false
  return true
}

export function normalizeSentenceReorderData(value: unknown): SentenceReorderData | null {
  if (!isRecord(value) || !hasText(value.activityId)) return null
  if (!Array.isArray(value.items) || value.items.length === 0) return null
  if (!value.items.every(isSentenceReorderItem)) return null

  const itemIds = value.items.map((item) => item.id)
  if (new Set(itemIds).size !== itemIds.length) return null
  return value as unknown as SentenceReorderData
}

export function sentenceReorderFallback(data: SentenceReorderData) {
  const item = data.items[0]
  if (!item) return '### 重组成句练习'
  const tokenById = new Map(item.tokens.map((token) => [token.id, token.text]))
  const sentence = item.acceptedOrders[0]
    ?.map((id) => tokenById.get(id))
    .filter((text): text is string => Boolean(text))
    .join(' ')
  return `### ${item.instruction}\n\n${sentence || item.tokens.map((token) => token.text).join(' / ')}`
}
