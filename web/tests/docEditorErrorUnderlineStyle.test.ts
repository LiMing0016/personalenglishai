import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../src/components/writing/DocEditor.vue', import.meta.url),
  'utf8',
)

test('error marks draw underline from the mark box instead of text glyph borders', () => {
  assert.ok(
    source.includes('box-decoration-break: clone'),
    'error mark styling should preserve full underline boxes around short words',
  )
  assert.ok(
    source.includes('background-image: var(--error-underline-image)'),
    'error underline should be painted by a background layer tied to the mark box',
  )
})
