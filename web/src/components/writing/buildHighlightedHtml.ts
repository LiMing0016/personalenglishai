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
}

const TYPE_PRIORITY: Record<string, number> = {
  spelling: 0,
  morphology: 1,
  subject_verb: 2,
  tense: 3,
  syntax: 4,
  article: 5,
  preposition: 6,
  collocation: 7,
  word_choice: 8,
  part_of_speech: 9,
  punctuation: 10,
  logic: 11,
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function buildHighlightedHtml(text: string, errors: ErrorSpan[]): string {
  if (!text) return ''
  if (!errors || errors.length === 0) return escapeHtml(text)

  // 1. span 校验/修正：若 span 指向的文本与 original 不匹配，尝试重新定位
  for (const e of errors) {
    const { start, end } = e.span
    if (!e.original || start < 0 || end > text.length || start >= end) continue
    const sliced = text.slice(start, end)
    if (sliced === e.original) continue
    // span 漂移了，尝试用 original 重新定位
    const idx = text.indexOf(e.original)
    if (idx !== -1) {
      e.span.start = idx
      e.span.end = idx + e.original.length
    }
  }

  // 2. 过滤无效 error（零长度 span 或越界）
  const validErrors = errors.filter((e) => {
    const { start, end } = e.span
    if (start >= end) return false
    if (start < 0 || end > text.length) return false
    return true
  })

  if (validErrors.length === 0) return escapeHtml(text)

  // 3. 收集边界点
  const breakpointSet = new Set<number>()
  breakpointSet.add(0)
  breakpointSet.add(text.length)
  for (const e of validErrors) {
    breakpointSet.add(e.span.start)
    breakpointSet.add(e.span.end)
  }
  const breakpoints = Array.from(breakpointSet).sort((a, b) => a - b)

  // 4. 逐 segment 构建 HTML
  const parts: string[] = []
  for (let i = 0; i < breakpoints.length - 1; i++) {
    const segStart = breakpoints[i]
    const segEnd = breakpoints[i + 1]
    const segText = escapeHtml(text.slice(segStart, segEnd))

    // 找出覆盖此 segment 的所有 error
    const covering = validErrors.filter(
      (e) => e.span.start <= segStart && e.span.end >= segEnd,
    )

    if (covering.length === 0) {
      parts.push(segText)
    } else {
      // 取最高优先级的 error 做主 class
      const primary = covering.reduce((best, cur) => {
        if (cur.severity === 'major' && best.severity !== 'major') return cur
        if (cur.severity !== 'major' && best.severity === 'major') return best
        return (TYPE_PRIORITY[cur.type] ?? 99) < (TYPE_PRIORITY[best.type] ?? 99) ? cur : best
      })
      const ids = covering.map((e) => e.id).join(',')
      parts.push(
        `<mark class="err-${primary.type} err-${primary.severity}" data-error-ids="${ids}">${segText}</mark>`,
      )
    }
  }

  return parts.join('')
}
