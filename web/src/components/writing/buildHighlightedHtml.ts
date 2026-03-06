/**
 * 将纯文本 + 错误 span 数组转换为带 <mark> 高亮标签的 HTML。
 *
 * 处理逻辑：
 * 1. span 校验/修正：若 span 指向的文本与 original 不匹配，用 original 重新定位
 * 2. 过滤无效 span（start>=end 即零长度、越界）
 * 3. 收集所有 span 边界点，切分文本为 segments
 * 4. 每个 segment 检查被哪些 error 覆盖，有则包裹 <mark>
 * 5. 重叠 span：同一 segment 被多个 error 覆盖时合并到一个 <mark>
 */

export interface ErrorSpan {
  id: string
  type: string
  severity: 'minor' | 'major'
  span: { start: number; end: number }
  original?: string
  suggestion?: string
  reason?: string
}

const TYPE_PRIORITY: Record<string, number> = {
  spelling: 0,
  morphology: 1,
  subject_verb: 2,
  tense: 3,
  syntax: 4,
  plural: 5,
  countability: 6,
  comparative: 7,
  article: 8,
  preposition: 9,
  collocation: 10,
  word_choice: 11,
  part_of_speech: 12,
  punctuation: 13,
  logic: 14,
  register_style: 15,
  clarity: 16,
  redundancy: 17,
}

function findClosestMatch(
  text: string,
  needle: string,
  hintPos: number,
  caseSensitive = true,
): number {
  const haystack = caseSensitive ? text : text.toLowerCase()
  const target = caseSensitive ? needle : needle.toLowerCase()
  let bestIdx = -1
  let bestDist = Infinity
  let searchFrom = 0
  while (searchFrom <= haystack.length - target.length) {
    const idx = haystack.indexOf(target, searchFrom)
    if (idx === -1) break
    const dist = Math.abs(idx - hintPos)
    if (dist < bestDist) {
      bestDist = dist
      bestIdx = idx
    }
    searchFrom = idx + 1
  }
  return bestIdx
}

function resolveByNormalizedWhitespace(
  text: string,
  original: string,
): { start: number; end: number } | null {
  const normText = text.replace(/\s+/g, ' ')
  const normOrig = original.replace(/\s+/g, ' ')
  const idxNorm = normText.indexOf(normOrig)
  if (idxNorm === -1) return null

  let origPos = 0
  let normPos = 0
  while (normPos < idxNorm && origPos < text.length) {
    if (/\s/.test(text[origPos]) && normPos > 0 && normText[normPos - 1] === ' ') {
      origPos++
      continue
    }
    origPos++
    normPos++
  }

  const start = origPos
  let remaining = normOrig.length
  while (remaining > 0 && origPos < text.length) {
    if (/\s/.test(text[origPos]) && origPos > start && /\s/.test(text[origPos - 1])) {
      origPos++
      continue
    }
    origPos++
    remaining--
  }
  return origPos > start ? { start, end: origPos } : null
}

function resolveSpanByOriginal(
  text: string,
  original: string,
  startHint: number,
): { start: number; end: number } | null {
  const idxExact = findClosestMatch(text, original, startHint, true)
  if (idxExact !== -1) {
    return { start: idxExact, end: idxExact + original.length }
  }

  const idxInsensitive = findClosestMatch(text, original, startHint, false)
  if (idxInsensitive !== -1) {
    return { start: idxInsensitive, end: idxInsensitive + original.length }
  }

  return resolveByNormalizedWhitespace(text, original)
}

function trimEdgeWhitespace(text: string, start: number, end: number): { start: number; end: number } {
  let s = start
  let e = end
  while (s < e && /\s/.test(text[s])) s++
  while (e > s && /\s/.test(text[e - 1])) e--
  return { start: s, end: e }
}

function isWordChar(ch: string): boolean {
  return /[A-Za-z0-9'_/-]/.test(ch)
}

function snapToWordBoundary(
  text: string,
  start: number,
  end: number,
): { start: number; end: number } {
  let s = start
  let e = end

  const cutsLeftWord =
    s > 0 && s < text.length && isWordChar(text[s - 1]) && isWordChar(text[s])
  const cutsRightWord =
    e > 0 && e < text.length && isWordChar(text[e - 1]) && isWordChar(text[e])

  if (cutsLeftWord) {
    while (s > 0 && isWordChar(text[s - 1])) s--
  }
  if (cutsRightWord) {
    while (e < text.length && isWordChar(text[e])) e++
  }
  return { start: s, end: e }
}

const SENTENCE_HIGHLIGHT_INLINE_STYLE = 'background:rgba(59,130,246,.2);border-bottom:2px solid #2563eb;border-radius:3px;box-shadow:inset 0 0 0 1px rgba(37,99,235,.2);'

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export interface HighlightRange {
  start: number
  end: number
}

function buildHighlightOnlyHtml(text: string, highlightRange?: HighlightRange | null): string {
  if (!highlightRange) return escapeHtml(text)
  const s = Math.max(0, highlightRange.start)
  const e = Math.min(text.length, highlightRange.end)
  if (s >= e) return escapeHtml(text)
  return escapeHtml(text.slice(0, s)) +
    `<span class="sentence-hl" style="${SENTENCE_HIGHLIGHT_INLINE_STYLE}">${escapeHtml(text.slice(s, e))}</span>` +
    escapeHtml(text.slice(e))
}

function toNormalizedLfIndex(rawText: string, rawIndex: number): number {
  const capped = Math.max(0, Math.min(rawText.length, rawIndex))
  let removed = 0
  for (let i = 0; i < capped; i++) {
    if (rawText[i] === '\r' && rawText[i + 1] === '\n') {
      removed++
    }
  }
  return capped - removed
}

function normalizeHighlightRange(
  rawText: string,
  range?: HighlightRange | null,
): HighlightRange | null {
  if (!range) return null
  const startRaw = Math.max(0, Math.min(rawText.length, range.start))
  const endRaw = Math.max(0, Math.min(rawText.length, range.end))
  const s = toNormalizedLfIndex(rawText, startRaw)
  const e = toNormalizedLfIndex(rawText, endRaw)
  return s <= e ? { start: s, end: e } : { start: e, end: s }
}

export function buildHighlightedHtml(
  rawText: string,
  errors: ErrorSpan[],
  highlightRange?: HighlightRange | null,
): string {
  if (!rawText) return ''
  // 统一换行符，与后端 span 计算保持一致
  const text = rawText.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const normalizedHighlightRange = normalizeHighlightRange(rawText, highlightRange)
  if ((!errors || errors.length === 0) && !normalizedHighlightRange) return escapeHtml(text)

  // Handle highlight-only case (no errors)
  if (!errors || errors.length === 0) {
    return buildHighlightOnlyHtml(text, normalizedHighlightRange)
  }

  // 1. 深拷贝 errors，避免修改外部 reactive 对象
  const localErrors: ErrorSpan[] = errors.map((e) => ({
    ...e,
    span: { start: e.span.start, end: e.span.end },
  }))

  // 2. span 校验/修正：若 span 漂移，按 original 重新定位；再做空白裁剪和词边界对齐
  for (const e of localErrors) {
    let start = Math.max(0, e.span.start)
    let end = Math.min(text.length, e.span.end)
    if (start >= end) continue

    if (e.original) {
      const sliced = text.slice(start, end)
      if (sliced !== e.original) {
        const resolved = resolveSpanByOriginal(text, e.original, start)
        if (resolved) {
          start = resolved.start
          end = resolved.end
        }
      }
    }

    const trimmed = trimEdgeWhitespace(text, start, end)
    const snapped = snapToWordBoundary(text, trimmed.start, trimmed.end)
    e.span.start = snapped.start
    e.span.end = snapped.end
  }

  // 3. 过滤无效 error（零长度 span 或越界）
  const validErrors = localErrors.filter((e) => {
    const { start, end } = e.span
    if (start >= end) return false
    if (start < 0 || end > text.length) return false
    return true
  })

  if (validErrors.length === 0) return buildHighlightOnlyHtml(text, normalizedHighlightRange)

  // 4. 收集边界点
  const breakpointSet = new Set<number>()
  breakpointSet.add(0)
  breakpointSet.add(text.length)
  for (const e of validErrors) {
    breakpointSet.add(e.span.start)
    breakpointSet.add(e.span.end)
  }

  // Add highlight range breakpoints
  const hlStart = normalizedHighlightRange ? Math.max(0, normalizedHighlightRange.start) : -1
  const hlEnd = normalizedHighlightRange ? Math.min(text.length, normalizedHighlightRange.end) : -1
  const hasHl = hlStart >= 0 && hlEnd > hlStart
  if (hasHl) {
    breakpointSet.add(hlStart)
    breakpointSet.add(hlEnd)
  }

  const breakpoints = Array.from(breakpointSet).sort((a, b) => a - b)

  // 5. 逐 segment 构建 HTML
  const parts: string[] = []
  for (let i = 0; i < breakpoints.length - 1; i++) {
    const segStart = breakpoints[i]
    const segEnd = breakpoints[i + 1]
    const segText = escapeHtml(text.slice(segStart, segEnd))
    const inHl = hasHl && segStart >= hlStart && segEnd <= hlEnd

    // 找出覆盖此 segment 的所有 error
    const covering = validErrors.filter(
      (e) => e.span.start <= segStart && e.span.end >= segEnd,
    )

    let html: string
    if (covering.length === 0) {
      html = segText
    } else {
      // 取最高优先级的 error 做主 class
      const primary = covering.reduce((best, cur) => {
        if (cur.severity === 'major' && best.severity !== 'major') return cur
        if (cur.severity !== 'major' && best.severity === 'major') return best
        return (TYPE_PRIORITY[cur.type] ?? 99) < (TYPE_PRIORITY[best.type] ?? 99) ? cur : best
      })
      const ids = covering.map((e) => e.id).join(',')
      html = `<mark class="err-${primary.type} err-${primary.severity}" data-error-ids="${ids}">${segText}</mark>`
    }

    parts.push(inHl ? `<span class="sentence-hl" style="${SENTENCE_HIGHLIGHT_INLINE_STYLE}">${html}</span>` : html)
  }

  return parts.join('')
}








