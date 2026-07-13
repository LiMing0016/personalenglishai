import test from 'node:test'
import assert from 'node:assert/strict'

import { renderAssistantMarkdown, renderMarkdownDocument } from './markdown.ts'

test('strict markdown escapes html breaks and disables images', () => {
  const document = renderMarkdownDocument(
    'first<br>second\n\n![secret](https://example.com/secret.png)',
    { allowHtmlBreaks: false, allowImages: false },
  )

  assert.match(document.html, /&lt;br&gt;/)
  assert.doesNotMatch(document.html, /<br\/>|<img/)
  assert.match(document.html, /secret/)
})

test('markdown document extracts ordered h2 sections with unique ids', () => {
  const document = renderMarkdownDocument('## 例句\n\nA\n\n## **例句**\n\nB', {
    headingAnchors: true,
  })

  assert.deepEqual(document.sections, [
    { id: 'markdown-section-1', title: '例句', level: 2 },
    { id: 'markdown-section-2', title: '例句', level: 2 },
  ])
  assert.match(document.html, /id="markdown-section-1"/)
  assert.match(document.html, /id="markdown-section-2"/)
})

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

test('renderAssistantMarkdown allows safe br tags in generated content', () => {
  const html = renderAssistantMarkdown('必答点：1. 明确立场<br>2. 展开理由')

  assert.equal(html, '<p>必答点：1. 明确立场<br/>2. 展开理由</p>')
})

test('renderAssistantMarkdown renders fenced code blocks', () => {
  const html = renderAssistantMarkdown(
    [
      '审题阶段应该升级成：',
      '',
      '```text',
      '题目 + 材料 + 图片描述/附件 + 学段 + 题型',
      '↓',
      '审题 Structured Output',
      '```',
    ].join('\n'),
  )

  assert.match(html, /<p>审题阶段应该升级成：<\/p>/)
  assert.match(html, /<div class="markdown-code-block">/)
  assert.match(html, /<div class="markdown-code-header"><span>text<\/span><button type="button" class="markdown-code-copy" data-markdown-code-copy aria-label="复制文本">复制<\/button><\/div>/)
  assert.match(html, /<pre><code>题目 \+ 材料 \+ 图片描述\/附件 \+ 学段 \+ 题型\n↓\n审题 Structured Output<\/code><\/pre>/)
  assert.doesNotMatch(html, /```text/)
})

test('renderAssistantMarkdown renders h4-h6 headings', () => {
  const html = renderAssistantMarkdown(
    [
      '#### 核心观点',
      '##### 可展开句',
      '###### 这一段的关键词',
    ].join('\n'),
  )

  assert.match(html, /<h4>核心观点<\/h4>/)
  assert.match(html, /<h5>可展开句<\/h5>/)
  assert.match(html, /<h6>这一段的关键词<\/h6>/)
  assert.doesNotMatch(html, /#### 核心观点/)
})
