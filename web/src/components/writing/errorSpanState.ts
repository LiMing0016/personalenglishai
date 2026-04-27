import { resolveErrorSpan, type SpanSource } from './errorSpanResolver.ts'

export type ErrorWithSpan = SpanSource

function isWordChar(ch: string): boolean {
  return /[A-Za-z0-9'_/-]/.test(ch)
}

function expandSingleLetterFragment(error: ErrorWithSpan, text: string) {
  const original = error.original ?? ''
  if (!/^[A-Za-z]$/.test(original) || /^[aI]$/.test(original)) return null

  const { start, end } = error.span
  if (start < 0 || end > text.length || start >= end || text.slice(start, end) !== original) {
    return null
  }

  const cutsWord =
    (start > 0 && isWordChar(text[start - 1])) ||
    (end < text.length && isWordChar(text[end]))
  if (!cutsWord) return null

  let wordStart = start
  let wordEnd = end
  while (wordStart > 0 && isWordChar(text[wordStart - 1])) wordStart -= 1
  while (wordEnd < text.length && isWordChar(text[wordEnd])) wordEnd += 1

  if (wordEnd - wordStart <= 1) return null
  return { start: wordStart, end: wordEnd, original: text.slice(wordStart, wordEnd) }
}

export function resolveVisibleErrorSpans<T extends ErrorWithSpan>(errors: T[], text: string): T[] {
  if (!text || errors.length === 0) return errors

  return errors.map((error) => {
    if (!error.original || !error.span) return error

    const resolved = resolveErrorSpan(error, text)
    if (!resolved) {
      const expanded = expandSingleLetterFragment(error, text)
      if (!expanded) return error
      return {
        ...error,
        original: expanded.original,
        span: { start: expanded.start, end: expanded.end },
      }
    }
    if (resolved.start === error.span.start && resolved.end === error.span.end) return error

    return {
      ...error,
      span: resolved,
    }
  })
}
