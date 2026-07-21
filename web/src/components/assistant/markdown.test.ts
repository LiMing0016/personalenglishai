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

  assert.match(html, /<table(?:\s[^>]*)?>/)
  assert.match(html, /<thead>/)
  assert.match(html, /<th[^>]*>词<\/th>/)
  assert.match(html, /<tbody>/)
  assert.match(html, /<td[^>]*>important<\/td>/)
  assert.match(html, /<td[^>]*>客观影响\/统计意义<\/td>/)
})

test('renderAssistantMarkdown keeps non-table pipes as paragraph text', () => {
  const html = renderAssistantMarkdown('A | B is not a table.')

  assert.equal(html, '<p>A | B is not a table.</p>')
})

test('renderAssistantMarkdown allows safe br tags in generated content', () => {
  const html = renderAssistantMarkdown('必答点：1. 明确立场<br>2. 展开理由')

  assert.match(html, /^<p>必答点：1\. 明确立场<br>\s*2\. 展开理由<\/p>$/)
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
  assert.match(html, /<div class="markdown-code-block(?:\s[^"]*)?">/)
  assert.match(html, /markdown-code-block--wrap/)
  assert.match(html, /<div class="markdown-code-header"><span>text<\/span><button[^>]*class="markdown-code-copy"[^>]*aria-label="复制文本"[^>]*>复制<\/button><\/div>/)
  assert.match(html, /<pre><code[^>]*>题目 \+ 材料 \+ 图片描述\/附件 \+ 学段 \+ 题型\n↓\n审题 Structured Output\n?<\/code><\/pre>/)
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

test('renderAssistantMarkdown preserves nested list structure', () => {
  const html = renderAssistantMarkdown(
    [
      '- 检查句子',
      '  - 检查时态',
      '  - 检查主谓一致',
      '- 完成修改',
    ].join('\n'),
  )

  assert.match(html, /<li>检查句子\s*<ul>/)
  assert.match(html, /<li>检查时态<\/li>/)
  assert.match(html, /<li>检查主谓一致<\/li>/)
})

test('renderAssistantMarkdown supports common GFM inline and task-list syntax', () => {
  const html = renderAssistantMarkdown(
    [
      '这是 *重点*，不是 ~~旧说法~~。',
      '',
      '- [x] 已掌握',
      '- [ ] 待复习',
    ].join('\n'),
  )

  assert.match(html, /<em>重点<\/em>/)
  assert.match(html, /<del>旧说法<\/del>/)
  assert.match(html, /type="checkbox"[^>]*checked/)
  assert.match(html, /type="checkbox"[^>]*disabled/)
})

test('renderAssistantMarkdown makes safe external links actionable and blocks unsafe links', () => {
  const html = renderAssistantMarkdown(
    [
      '[查看语法资料](https://example.com/grammar)',
      '',
      '[危险链接](javascript:alert(1))',
    ].join('\n'),
  )

  assert.match(html, /href="https:\/\/example\.com\/grammar"/)
  assert.match(html, /target="_blank"/)
  assert.match(html, /rel="noopener noreferrer"/)
  assert.doesNotMatch(html, /href="javascript:/)
})

test('renderAssistantMarkdown exposes wide tables as keyboard-scrollable regions', () => {
  const html = renderAssistantMarkdown(
    [
      '| 表达 | 评价 | 说明 |',
      '|---|---|---|',
      '| example | 自然 | 示例说明 |',
    ].join('\n'),
  )

  assert.match(html, /class="markdown-table-scroll(?:\s[^"]*)?"/)
  assert.match(html, /tabindex="0"/)
  assert.match(html, /role="region"/)
  assert.match(html, /aria-label="可横向滚动的数据表格"/)
  assert.match(html, /markdown-table--responsive-cards/)
  assert.match(html, /data-label="表达"/)
  assert.match(html, /data-label="评价"/)
})

test('renderAssistantMarkdown keeps code language metadata and announces copy feedback', () => {
  const html = renderAssistantMarkdown(
    [
      '```python',
      'if student:',
      '    start_practice()',
      '```',
    ].join('\n'),
  )

  assert.match(html, /<code class="language-python">/)
  assert.doesNotMatch(html, /markdown-code-block--wrap/)
  assert.match(html, /data-markdown-code-copy/)
  assert.match(html, /aria-live="polite"/)
})
