import type {
  VocabularyCardFilters,
  VocabularyCardPage,
  VocabularyCardStatus,
  VocabularyCardSummary,
} from '@/api/vocabulary'

type RouteQueryLike = Record<string, unknown>

export interface VocabularyCardSequenceTarget {
  cardUid: string
  displayTerm: string
}

export interface VocabularyCardSequence {
  previous: VocabularyCardSequenceTarget | null
  next: VocabularyCardSequenceTarget | null
  hasPrevious: boolean
  hasNext: boolean
  position: number
  total: number
}

const supportedStatuses = new Set<VocabularyCardStatus>([
  'captured',
  'generating',
  'ready',
  'needs_review',
  'failed',
])

function firstQueryValue(value: unknown): string | undefined {
  const candidate = Array.isArray(value) ? value[0] : value
  return typeof candidate === 'string' ? candidate : undefined
}

function positiveInteger(value: unknown, fallback: number, maximum?: number) {
  const parsed = Number.parseInt(firstQueryValue(value) ?? '', 10)
  if (!Number.isFinite(parsed) || parsed < 1) return fallback
  return maximum ? Math.min(parsed, maximum) : parsed
}

export function buildVocabularyNavigationQuery(filters: VocabularyCardFilters): Record<string, string> {
  const query: Record<string, string> = {
    vc: '1',
    sort: filters.sort ?? 'recent',
    page: String(Math.max(1, filters.page ?? 1)),
    size: String(Math.min(100, Math.max(1, filters.size ?? 20))),
  }
  if (filters.keyword?.trim()) query.keyword = filters.keyword.trim()
  if (filters.status) query.status = filters.status
  if (filters.sourceType?.trim()) query.source = filters.sourceType.trim()
  return query
}

export function parseVocabularyNavigationQuery(query: RouteQueryLike): VocabularyCardFilters | null {
  if (firstQueryValue(query.vc) !== '1') return null

  const keyword = firstQueryValue(query.keyword)?.trim()
  const statusValue = firstQueryValue(query.status)
  const sourceType = firstQueryValue(query.source)?.trim()
  const sortValue = firstQueryValue(query.sort)

  return {
    ...(keyword ? { keyword } : {}),
    ...(statusValue && supportedStatuses.has(statusValue as VocabularyCardStatus)
      ? { status: statusValue as VocabularyCardStatus }
      : {}),
    ...(sourceType ? { sourceType } : {}),
    sort: sortValue === 'az' ? 'az' : 'recent',
    page: positiveInteger(query.page, 1),
    size: positiveInteger(query.size, 20, 100),
  }
}

function target(card: VocabularyCardSummary | undefined): VocabularyCardSequenceTarget | null {
  return card ? { cardUid: card.cardUid, displayTerm: card.displayTerm } : null
}

export function resolveVocabularyCardSequence(
  currentPage: VocabularyCardPage,
  cardUid: string,
  previousPage?: VocabularyCardPage | null,
  nextPage?: VocabularyCardPage | null,
): VocabularyCardSequence | null {
  const index = currentPage.items.findIndex((card) => card.cardUid === cardUid)
  if (index < 0) return null

  const position = (currentPage.page - 1) * currentPage.size + index + 1
  const previous = index > 0
    ? target(currentPage.items[index - 1])
    : target(previousPage?.items.at(-1))
  const next = index < currentPage.items.length - 1
    ? target(currentPage.items[index + 1])
    : target(nextPage?.items[0])

  return {
    previous,
    next,
    hasPrevious: position > 1,
    hasNext: position < currentPage.total,
    position,
    total: currentPage.total,
  }
}
