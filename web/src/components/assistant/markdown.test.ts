import test from 'node:test'
import assert from 'node:assert/strict'

import { renderAssistantMarkdown } from './markdown.ts'

test('renderAssistantMarkdown renders GFM tables as table elements', () => {
  const html = renderAssistantMarkdown(
    [
      '| 词 | 核心含义 | 重点 |',
      '|---|---|---|',
      '| important | 有价值、需要重视 | 主观/实际重要性 |',
      '| significant | 程度大、意义明显 | 客观影响/统计意义 |',
    ].join('\n'),
  )

  assert.match(html, /<table>/)
  assert.match(html, /<thead>/)
  assert.match(html, /<th>词<\/th>/)
  assert.match(html, /<tbody>/)
  assert.match(html, /<td>important<\/td>/)
  assert.match(html, /<td>客观影响\/统计意义<\/td>/)
})

test('renderAssistantMarkdown keeps non-table pipes as paragraph text', () => {
  const html = renderAssistantMarkdown('A | B is not a table.')

  assert.equal(html, '<p>A | B is not a table.</p>')
})
