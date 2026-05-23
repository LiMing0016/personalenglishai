import type { WritingPatch } from '../../types/assistantRequest'

export type WritingPatchApplyResult =
  | {
      status: 'success'
      nextText: string
      preview: {
        before?: string
        after: string
        operationLabel: string
      }
      appliedRange: { start: number; end: number }
      cursorAt: number
    }
  | {
      status: 'not_found'
      message: string
    }
  | {
      status: 'ambiguous'
      message: string
      candidates: Array<{ start: number; end: number; preview: string }>
    }
  | {
      status: 'duplicate'
      message: string
    }

export function applyWritingPatch(draftText: string, patch: WritingPatch): WritingPatchApplyResult {
  if (patch.op === 'replace_selection') {
    return applyReplaceSelectionPatch(draftText, patch)
  }
  if (patch.op === 'search_replace') {
    return applySearchReplacePatch(draftText, patch.searchText, patch.replaceText, '替换文本')
  }
  if (patch.op === 'insert_after_anchor') {
    return applyInsertAfterAnchorPatch(draftText, patch)
  }
  if (patch.op === 'append_paragraph') {
    return applyAppendParagraphPatch(draftText, patch.text)
  }
  return applyReplaceDocumentPatch(patch.text)
}

function applyReplaceSelectionPatch(
  draftText: string,
  patch: Extract<WritingPatch, { op: 'replace_selection' }>,
): WritingPatchApplyResult {
  const range = normalizeRange(patch.range.start, patch.range.end, draftText.length)
  if (range.start === range.end) {
    return { status: 'not_found', message: '选区为空，无法替换。' }
  }

  const selectedText = draftText.slice(range.start, range.end)
  if (patch.originalText && selectedText !== patch.originalText) {
    return applySearchReplacePatch(draftText, patch.originalText, patch.newText, '替换选区')
  }

  const nextText = replaceRange(draftText, range.start, range.end, patch.newText)
  return {
    status: 'success',
    nextText,
    preview: {
      before: selectedText,
      after: patch.newText,
      operationLabel: '替换选区',
    },
    appliedRange: range,
    cursorAt: range.start + patch.newText.length,
  }
}

function applySearchReplacePatch(
  draftText: string,
  searchText: string,
  replaceText: string,
  operationLabel: string,
): WritingPatchApplyResult {
  const search = searchText.trim()
  const replacement = replaceText.trim()
  if (!search) return { status: 'not_found', message: '缺少要定位的原文。' }
  if (!replacement) return { status: 'not_found', message: '建议内容为空。' }

  const matches = findExactMatches(draftText, search)
  if (matches.length === 0) {
    return { status: 'not_found', message: '无法在正文中定位原文，请重新选择目标句子。' }
  }
  if (matches.length > 1) {
    return {
      status: 'ambiguous',
      message: '原文在正文中出现多次，请先选择要修改的位置。',
      candidates: matches.map((match) => ({
        start: match.start,
        end: match.end,
        preview: excerptAround(draftText, match.start, match.end),
      })),
    }
  }

  const match = matches[0]!
  const nextText = replaceRange(draftText, match.start, match.end, replacement)
  return {
    status: 'success',
    nextText,
    preview: {
      before: search,
      after: replacement,
      operationLabel,
    },
    appliedRange: match,
    cursorAt: match.start + replacement.length,
  }
}

function applyInsertAfterAnchorPatch(
  draftText: string,
  patch: Extract<WritingPatch, { op: 'insert_after_anchor' }>,
): WritingPatchApplyResult {
  const anchor = patch.anchorText.trim()
  const insertion = patch.insertText.trim()
  if (!anchor) return { status: 'not_found', message: '缺少插入位置的锚点文本。' }
  if (!insertion) return { status: 'not_found', message: '要插入的内容为空。' }

  const matches = findExactMatches(draftText, anchor)
  if (matches.length === 0) {
    return { status: 'not_found', message: '无法在正文中定位插入位置，请重新选择目标句子。' }
  }
  if (matches.length > 1) {
    return {
      status: 'ambiguous',
      message: '锚点文本在正文中出现多次，请先选择要插入的位置。',
      candidates: matches.map((match) => ({
        start: match.start,
        end: match.end,
        preview: excerptAround(draftText, match.start, match.end),
      })),
    }
  }

  const match = matches[0]!
  if (containsNearby(draftText, match.end, insertion)) {
    return { status: 'duplicate', message: '相同或非常接近的内容已经在附近，未重复插入。' }
  }

  const separator = resolveInlineInsertSeparator(draftText, match.end)
  const inserted = `${separator}${insertion}`
  const nextText = draftText.slice(0, match.end) + inserted + draftText.slice(match.end)
  return {
    status: 'success',
    nextText,
    preview: {
      before: anchor,
      after: `${anchor}${inserted}`,
      operationLabel: '插入到锚点后',
    },
    appliedRange: { start: match.end, end: match.end + inserted.length },
    cursorAt: match.end + inserted.length,
  }
}

function applyAppendParagraphPatch(draftText: string, paragraph: string): WritingPatchApplyResult {
  const text = paragraph.trim()
  if (!text) return { status: 'not_found', message: '要追加的段落为空。' }
  if (draftText.includes(text)) {
    return { status: 'duplicate', message: '正文中已经存在这段内容，未重复追加。' }
  }

  const separator = draftText.trim() ? '\n\n' : ''
  const inserted = `${separator}${text}`
  const nextText = `${draftText}${inserted}`
  return {
    status: 'success',
    nextText,
    preview: {
      after: text,
      operationLabel: '追加新段落',
    },
    appliedRange: { start: draftText.length + separator.length, end: nextText.length },
    cursorAt: nextText.length,
  }
}

function applyReplaceDocumentPatch(text: string): WritingPatchApplyResult {
  const replacement = text.trim()
  if (!replacement) return { status: 'not_found', message: '替换全文内容为空。' }
  return {
    status: 'success',
    nextText: replacement,
    preview: {
      after: replacement,
      operationLabel: '替换全文',
    },
    appliedRange: { start: 0, end: replacement.length },
    cursorAt: replacement.length,
  }
}

function findExactMatches(text: string, search: string): Array<{ start: number; end: number }> {
  const matches: Array<{ start: number; end: number }> = []
  let index = text.indexOf(search)
  while (index >= 0) {
    matches.push({ start: index, end: index + search.length })
    index = text.indexOf(search, index + search.length)
  }
  return matches
}

function replaceRange(text: string, start: number, end: number, replacement: string): string {
  return text.slice(0, start) + replacement + text.slice(end)
}

function normalizeRange(start: number, end: number, max: number): { start: number; end: number } {
  const safeStart = Math.max(0, Math.min(start, max))
  const safeEnd = Math.max(0, Math.min(end, max))
  return safeStart <= safeEnd
    ? { start: safeStart, end: safeEnd }
    : { start: safeEnd, end: safeStart }
}

function resolveInlineInsertSeparator(text: string, at: number): string {
  if (at <= 0) return ''
  const before = text[at - 1] ?? ''
  const after = text[at] ?? ''
  if (!before || /\s/u.test(before) || /\s/u.test(after)) return ''
  return ' '
}

function containsNearby(text: string, at: number, insertion: string): boolean {
  const windowStart = Math.max(0, at - 160)
  const windowEnd = Math.min(text.length, at + insertion.length + 240)
  return normalizeComparable(text.slice(windowStart, windowEnd)).includes(normalizeComparable(insertion))
}

function normalizeComparable(text: string): string {
  return text.replace(/\s+/g, ' ').trim().toLowerCase()
}

function excerptAround(text: string, start: number, end: number): string {
  const before = text.slice(Math.max(0, start - 48), start).trimStart()
  const target = text.slice(start, end)
  const after = text.slice(end, Math.min(text.length, end + 48)).trimEnd()
  return `${before}${target}${after}`
}
