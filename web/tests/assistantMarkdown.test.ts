import assert from 'node:assert/strict'

import { renderAssistantMarkdown } from '../src/components/assistant/markdown.ts'

const rendered = renderAssistantMarkdown([
  '## 你的原句',
  '> The table shows **average** data.',
  '',
  '### 主要问题',
  '- **owned per 100 households**',
  '- `show` should be `shows`',
].join('\n'))

assert.ok(rendered.includes('<h2>你的原句</h2>'))
assert.match(rendered, /<blockquote>\s*<p>The table shows <strong>average<\/strong> data\.<\/p>\s*<\/blockquote>/)
assert.ok(rendered.includes('<h3>主要问题</h3>'))
assert.match(rendered, /<ul>\s*<li><strong>owned per 100 households<\/strong><\/li>\s*<li><code>show<\/code> should be <code>shows<\/code><\/li>\s*<\/ul>/)

const ordered = renderAssistantMarkdown('1. 画面内容描述\n2. 对比关系\n3. 细节归纳')
assert.match(ordered, /<ol>\s*<li>画面内容描述<\/li>\s*<li>对比关系<\/li>\s*<li>细节归纳<\/li>\s*<\/ol>/)

const unsafe = renderAssistantMarkdown('<script>alert(1)</script>\n\n**safe**')
assert.ok(!unsafe.includes('<script>'))
assert.match(unsafe, /(?:&lt;|&#x3C;)script>alert\(1\)(?:&lt;|&#x3C;)\/script>/)
assert.ok(unsafe.includes('<strong>safe</strong>'))

const image = renderAssistantMarkdown('![例句截图](data:image/png;base64,abc)')
assert.ok(image.includes('<img'))
assert.ok(image.includes('src="data:image/png;base64,abc"'))
assert.ok(image.includes('alt="例句截图"'))

const unsafeImage = renderAssistantMarkdown('![bad](javascript:alert(1))')
assert.ok(!unsafeImage.includes('<img'))
assert.ok(!unsafeImage.includes('javascript:alert'))
