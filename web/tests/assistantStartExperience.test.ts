import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const page = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const chat = readFileSync(new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url), 'utf8')
const starters = readFileSync(
  new URL('../src/components/assistant/AssistantStarterCards.vue', import.meta.url),
  'utf8',
)
const composer = readFileSync(
  new URL('../src/components/assistant/AssistantComposer.vue', import.meta.url),
  'utf8',
)

assert.ok(page.includes("const emptyTitle = '今天想完成什么？'"))
assert.ok(page.includes("const emptySubtitle = '先选一个学习目标，再把内容发给我。'"))

for (const text of [
  '检查句子',
  '润色表达',
  '设计练习',
  '讲解词句',
  '检查这句话是否自然',
  '给出原因和改法',
  '帮我升级这段表达',
  '保留原意，更地道',
  '设计一道写作练习',
  '包含题目、思路和反馈',
]) {
  assert.ok(starters.includes(text), `missing starter copy: ${text}`)
}

assert.ok(starters.includes(':aria-pressed="selectedGoal === goal.id"'))
assert.ok(starters.includes('<slot name="composer"'))
assert.ok(chat.includes('<slot name="empty-composer"'))
assert.ok(composer.includes('defineExpose({ focus: focusTextarea })'))
assert.ok(page.includes('const selectedStarterGoal'))
assert.ok(page.includes('<template #empty-composer>'))
assert.ok(page.includes('v-if="activeConversation.messages.length === 0"'))
assert.ok(page.includes('v-if="activeConversation.messages.length > 0" class="composer-dock"'))
assert.ok(!page.includes('markdown-theme-control'))
assert.ok(chat.includes('overflow-wrap: anywhere'))
assert.ok(chat.includes('.message-content--markdown :deep(.markdown-table-scroll)'))
assert.ok(chat.includes('max-width: 100%'))
const emptyStateStyles = chat.slice(
  chat.indexOf('.empty-state {'),
  chat.indexOf('.empty-title {'),
)
assert.ok(emptyStateStyles.includes('width: min(920px, 100%)'))
assert.ok(page.includes("'把你的句子、段落或问题发给我…'"))
assert.ok(composer.includes('.assistant-composer--entry'))
assert.ok(composer.includes('min-height: 138px'))

console.log('assistant-start-experience-ok')
