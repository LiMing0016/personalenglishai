export const ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH = 1280
export const DEFAULT_ASSISTANT_SIDEBAR_WIDTH = 218
export const MIN_ASSISTANT_SIDEBAR_WIDTH = 200
export const MAX_ASSISTANT_SIDEBAR_WIDTH = 360

export function clampAssistantSidebarWidth(width: number) {
  return Math.min(Math.max(width, MIN_ASSISTANT_SIDEBAR_WIDTH), MAX_ASSISTANT_SIDEBAR_WIDTH)
}

export function shouldAutoCollapseAssistantSidebar(options: {
  learningCanvasOpen: boolean
  viewportWidth: number
}) {
  return (
    options.learningCanvasOpen
    || options.viewportWidth <= ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH
  )
}

interface AssistantConversationGroupItem {
  title: string
  summary: string
  createdAt: number
  updatedAt: number
}

export interface AssistantConversationGroup<T> {
  label: '今天' | '最近 7 天' | '更早'
  conversations: T[]
}

function startOfLocalDay(timestamp: number) {
  const date = new Date(timestamp)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

function isUntouchedEmptyConversation(conversation: AssistantConversationGroupItem) {
  return conversation.title.trim() === '新对话' && !conversation.summary.trim()
}

export function buildAssistantConversationGroups<T extends AssistantConversationGroupItem>(
  items: T[],
  now = Date.now(),
): AssistantConversationGroup<T>[] {
  const todayStart = startOfLocalDay(now)
  const recentStartDate = new Date(todayStart)
  recentStartDate.setDate(recentStartDate.getDate() - 6)
  const recentStart = recentStartDate.getTime()
  const visibleItems = items.filter((conversation) => !isUntouchedEmptyConversation(conversation))

  return [
    {
      label: '今天' as const,
      conversations: visibleItems.filter((conversation) => conversation.createdAt >= todayStart),
    },
    {
      label: '最近 7 天' as const,
      conversations: visibleItems.filter((conversation) => (
        conversation.createdAt >= recentStart && conversation.createdAt < todayStart
      )),
    },
    {
      label: '更早' as const,
      conversations: visibleItems.filter((conversation) => conversation.createdAt < recentStart),
    },
  ].filter((group) => group.conversations.length > 0)
}
