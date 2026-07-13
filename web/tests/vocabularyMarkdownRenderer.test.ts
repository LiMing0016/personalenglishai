import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

function readSource(path: string) {
  const url = new URL(path, import.meta.url)
  return existsSync(url) ? readFileSync(url, 'utf8') : ''
}

const rendererSource = readSource('../src/components/vocabulary/VocabularyMarkdownRenderer.vue')

test('renders vocabulary markdown through the shared strict renderer', () => {
  assert.match(rendererSource, /renderMarkdownDocument/)
  assert.match(rendererSource, /copyMarkdownCodeFromClick/)
  assert.match(rendererSource, /allowImages:\s*false/)
  assert.match(rendererSource, /allowHtmlBreaks:\s*false/)
  assert.match(rendererSource, /headingAnchors:\s*true/)
  assert.match(rendererSource, /v-html="document\.html"/)
  assert.match(rendererSource, /@click="copyMarkdownCodeFromClick"/)
})

test('shows the empty state and publishes markdown sections', () => {
  assert.match(rendererSource, /暂无主题内容/)
  assert.match(rendererSource, /sections-change/)
})
