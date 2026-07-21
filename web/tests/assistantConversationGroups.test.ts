import assert from 'node:assert/strict'

const stateModuleUrl = new URL('../src/pages/app/assistantSidebarState.ts', import.meta.url)
const stateModule = await import(stateModuleUrl.href)

assert.equal(
  typeof stateModule.buildAssistantConversationGroups,
  'function',
  'assistant sidebar should expose calendar-day conversation grouping',
)

const now = new Date(2026, 6, 21, 10, 0, 0).getTime()
const timestamp = (year: number, month: number, day: number, hour = 12) => (
  new Date(year, month - 1, day, hour, 0, 0).getTime()
)

const groups = stateModule.buildAssistantConversationGroups([
  {
    id: 'empty-today',
    title: '新对话',
    summary: '',
    createdAt: timestamp(2026, 7, 21, 9),
    updatedAt: timestamp(2026, 7, 21, 9),
  },
  {
    id: 'created-today',
    title: '今天创建',
    summary: '内容',
    createdAt: timestamp(2026, 7, 21, 8),
    updatedAt: timestamp(2026, 7, 21, 9),
  },
  {
    id: 'created-yesterday-updated-today',
    title: '昨天创建、今天更新',
    summary: '内容',
    createdAt: timestamp(2026, 7, 20, 23),
    updatedAt: timestamp(2026, 7, 21, 9),
  },
  {
    id: 'created-earlier-updated-today',
    title: '更早创建、今天更新',
    summary: '内容',
    createdAt: timestamp(2026, 6, 28),
    updatedAt: timestamp(2026, 7, 21, 9),
  },
], now)

assert.deepEqual(
  groups.map((group: { label: string; conversations: Array<{ id: string }> }) => ({
    label: group.label,
    ids: group.conversations.map((conversation) => conversation.id),
  })),
  [
    { label: '今天', ids: ['created-today'] },
    { label: '最近 7 天', ids: ['created-yesterday-updated-today'] },
    { label: '更早', ids: ['created-earlier-updated-today'] },
  ],
  'history should use creation calendar dates and omit untouched empty conversations',
)

console.log('assistant-conversation-groups-ok')
