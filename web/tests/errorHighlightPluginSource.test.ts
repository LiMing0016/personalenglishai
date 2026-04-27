import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../src/components/writing/tiptap/errorHighlightPlugin.ts', import.meta.url),
  'utf8',
)

test('error highlight plugin resolves spans against rendered editor text before decorating', () => {
  assert.ok(
    source.includes('resolveVisibleErrorSpans'),
    'plugin should re-resolve spans against the rendered TipTap text before creating decorations',
  )
  assert.ok(
    source.includes('const resolvedErrors = resolveVisibleErrorSpans(errors, text)'),
    'decorations should iterate resolved errors, not raw store spans',
  )
  assert.ok(
    source.includes('for (const error of resolvedErrors)'),
    'error decorations should be created from resolved editor spans',
  )
})
