export interface SpanLocation {
  start: number
  end: number
}

export interface SpanSource {
  original?: string
  suggestion?: string
  span: SpanLocation
}

export function isWordChar(ch: string): boolean {
  return /[A-Za-z0-9'_/-]/.test(ch)
}

export function shouldUseWordBoundary(needle: string): boolean {
  if (!needle) return false
  return isWordChar(needle[0]) && isWordChar(needle[needle.length - 1])
}

export function isWholeWordMatch(text: string, start: number, len: number): boolean {
  if (start < 0 || len <= 0) return false
  const end = start + len
  const leftOk = start === 0 || !isWordChar(text[start - 1])
  const rightOk = end >= text.length || !isWordChar(text[end])
  return leftOk && rightOk
}

export function findClosestMatch(
  text: string,
  needle: string,
  hintPos: number,
  caseSensitive = true,
  wholeWordOnly = false,
): number {
  const haystack = caseSensitive ? text : text.toLowerCase()
  const target = caseSensitive ? needle : needle.toLowerCase()
  let bestIdx = -1
  let bestDist = Infinity
  let searchFrom = 0

  while (searchFrom <= haystack.length - target.length) {
    const idx = haystack.indexOf(target, searchFrom)
    if (idx === -1) break

    if (wholeWordOnly && !isWholeWordMatch(text, idx, needle.length)) {
      searchFrom = idx + 1
      continue
    }

    const dist = Math.abs(idx - hintPos)
    if (dist < bestDist) {
      bestDist = dist
      bestIdx = idx
    }
    searchFrom = idx + 1
  }

  return bestIdx
}

export function resolveErrorSpan(err: SpanSource, text: string): SpanLocation | null {
  const { start, end } = err.span
  if (!err.original) return null

  const wholeWord = shouldUseWordBoundary(err.original)

  if (start >= 0 && end <= text.length && start < end && text.slice(start, end) === err.original) {
    if (!wholeWord || isWholeWordMatch(text, start, end - start)) {
      return { start, end }
    }
  }

  const idx = findClosestMatch(text, err.original, start, true, wholeWord)
  if (idx !== -1) {
    return { start: idx, end: idx + err.original.length }
  }

  const idxLower = findClosestMatch(text, err.original, start, false, wholeWord)
  if (idxLower !== -1) {
    return { start: idxLower, end: idxLower + err.original.length }
  }

  const normText = text.replace(/\s+/g, ' ')
  const normOrig = err.original.replace(/\s+/g, ' ')
  const idxNorm = normText.indexOf(normOrig)
  if (idxNorm === -1) {
    return null
  }

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

  const matchStart = origPos
  let matchLen = 0
  let remaining = normOrig.length
  let p = origPos
  while (remaining > 0 && p < text.length) {
    if (/\s/.test(text[p]) && matchLen > 0 && /\s/.test(text[p - 1])) {
      p++
      continue
    }
    p++
    remaining--
    matchLen = p - matchStart
  }

  if (!wholeWord || isWholeWordMatch(text, matchStart, matchLen)) {
    return { start: matchStart, end: matchStart + matchLen }
  }

  return null
}

export function hasValidSuggestion(err: { original?: string; suggestion?: string }): boolean {
  if (err.suggestion == null) return false
  const suggestion = err.suggestion.trim()
  if (/^n\/?a$/i.test(suggestion)) return false
  if (suggestion === '' && err.original?.trim()) return true
  if (!suggestion) return false
  if (err.original && suggestion === err.original.trim()) return false
  return true
}
