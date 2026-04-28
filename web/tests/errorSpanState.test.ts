import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveVisibleErrorSpans } from '../src/components/writing/errorSpanState.ts'

test('resolveVisibleErrorSpans realigns stale spans to the current text', () => {
  const text = [
    'Although online learning is useful, it cannot replace traditional learning completely.',
    'Schools should provide more chances for students to interact with each other, and teachers should guide students on how to use online resources correctly.',
  ].join('\n\n')

  const resolved = resolveVisibleErrorSpans(
    [
      {
        id: 'it',
        original: 'it',
        suggestion: 'to it',
        span: { start: 0, end: 2 },
      },
      {
        id: 'chances',
        original: 'chances',
        suggestion: 'opportunities',
        span: { start: 8, end: 15 },
      },
      {
        id: 'resources',
        original: 'use online resources correctly.',
        suggestion: 'correctly use online resources.',
        span: { start: 20, end: 51 },
      },
    ],
    text,
  )

  assert.equal(text.slice(resolved[0].span.start, resolved[0].span.end), 'it')
  assert.equal(text.slice(resolved[1].span.start, resolved[1].span.end), 'chances')
  assert.equal(
    text.slice(resolved[2].span.start, resolved[2].span.end),
    'use online resources correctly.',
  )
})

test('resolveVisibleErrorSpans expands single-letter engine fragments to the containing word', () => {
  const text = 'With the rapid development of economy, more families can afford appliances.'
  const start = text.indexOf('economy')

  const [resolved] = resolveVisibleErrorSpans(
    [
      {
        id: 'economy-fragment',
        original: 'e',
        suggestion: 'the economy',
        span: { start, end: start + 1 },
      },
    ],
    text,
  )

  assert.equal(text.slice(resolved.span.start, resolved.span.end), 'economy')
  assert.equal(resolved.original, 'economy')
})
