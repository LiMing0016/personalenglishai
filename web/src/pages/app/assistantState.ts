import { computed, ref } from 'vue'

import { assistantChat } from '../../api/assistant.ts'
import { stageCache } from '../../stores/stageCache.ts'
import {
  type AssistantAttachment,
  type AssistantMode,
  type AssistantReplyRequest,
  type AssistantConversation,
  type AssistantMessage,
} from './assistantMock.ts'

interface CreateAssistantStateOptions {
  buildReply?: (request: AssistantReplyRequest) => Promise<string>
  storage?: Storage
  storageKey?: string
}

const DEFAULT_STORAGE_KEY = 'peai:assistant:state:v1'

interface PersistedAssistantMessage {
  id: string
  role: AssistantMessage['role']
  content: string
  status: AssistantMessage['status']
}

interface PersistedAssistantConversation {
  id: string
  title: string
  summary: string
  updatedAt: number
  messages: PersistedAssistantMessage[]
}

interface PersistedAssistantState {
  activeConversationId: string
  conversations: PersistedAssistantConversation[]
}

function createId(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

function createEmptyConversation(): AssistantConversation {
  return {
    id: createId('conv'),
    title: '新对话',
    summary: '',
    updatedAt: Date.now(),
    messages: [],
  }
}

function createMessage(
  role: AssistantMessage['role'],
  content: string,
  status: AssistantMessage['status'],
): AssistantMessage {
  return {
    id: createId('msg'),
    role,
    content,
    status,
  }
}

function buildConversationTitle(input: string) {
  const trimmed = input.trim()
  if (!trimmed) {
    return '新对话'
  }
  return trimmed.length > 18 ? `${trimmed.slice(0, 18)}...` : trimmed
}

function currentStudyStage() {
  const stage = stageCache.value?.trim()
  return stage && stage !== '__error__' ? stage : undefined
}

function fallbackStorage(): Storage | undefined {
  try {
    return globalThis.localStorage
  } catch {
    return undefined
  }
}

function toPersistedConversation(conversation: AssistantConversation): PersistedAssistantConversation {
  return {
    id: conversation.id,
    title: conversation.title,
    summary: conversation.summary,
    updatedAt: conversation.updatedAt,
    messages: conversation.messages
      .filter((message) => message.status !== 'loading')
      .map((message) => ({
        id: message.id,
        role: message.role,
        content: message.content,
        status: 'done',
      })),
  }
}

function isPersistedRole(role: unknown): role is AssistantMessage['role'] {
  return role === 'user' || role === 'assistant'
}

function restoreConversation(value: unknown): AssistantConversation | null {
  const conversation = value as Partial<PersistedAssistantConversation> | null
  if (!conversation || typeof conversation !== 'object') return null
  if (typeof conversation.id !== 'string' || !conversation.id.trim()) return null

  const messages = Array.isArray(conversation.messages)
    ? conversation.messages
        .map((message): AssistantMessage | null => {
          if (!message || typeof message !== 'object') return null
          const candidate = message as Partial<PersistedAssistantMessage>
          if (typeof candidate.id !== 'string' || !candidate.id.trim()) return null
          if (!isPersistedRole(candidate.role)) return null
          if (typeof candidate.content !== 'string') return null
          return {
            id: candidate.id,
            role: candidate.role,
            content: candidate.content,
            status: 'done',
          } satisfies AssistantMessage
        })
        .filter((message): message is AssistantMessage => Boolean(message))
    : []

  return {
    id: conversation.id,
    title: typeof conversation.title === 'string' && conversation.title.trim() ? conversation.title : '新对话',
    summary: typeof conversation.summary === 'string' ? conversation.summary : '',
    updatedAt: typeof conversation.updatedAt === 'number' ? conversation.updatedAt : Date.now(),
    messages,
  }
}

function restoreAssistantState(storage: Storage | undefined, storageKey: string): PersistedAssistantState | null {
  if (!storage) return null
  try {
    const raw = storage.getItem(storageKey)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PersistedAssistantState>
    const conversations = Array.isArray(parsed.conversations)
      ? parsed.conversations
          .map((conversation) => restoreConversation(conversation))
          .filter((conversation): conversation is AssistantConversation => Boolean(conversation))
      : []
    if (conversations.length === 0) return null
    const activeConversationId =
      typeof parsed.activeConversationId === 'string' &&
      conversations.some((conversation) => conversation.id === parsed.activeConversationId)
        ? parsed.activeConversationId
        : conversations[0]!.id
    return { activeConversationId, conversations }
  } catch {
    return null
  }
}

export function createAssistantState(options: CreateAssistantStateOptions = {}) {
  const storage = options.storage ?? fallbackStorage()
  const storageKey = options.storageKey ?? DEFAULT_STORAGE_KEY
  const restored = restoreAssistantState(storage, storageKey)
  const conversations = ref<AssistantConversation[]>(restored?.conversations ?? [createEmptyConversation()])
  const activeConversationId = ref(restored?.activeConversationId ?? conversations.value[0]!.id)
  const composerText = ref('')
  const composerAttachments = ref<AssistantAttachment[]>([])
  const assistantMode = ref<AssistantMode>('default')
  const searchText = ref('')
  const isSending = ref(false)
  const errorMessage = ref('')
  const lastFailedPrompt = ref('')
  const lastFailedAttachments = ref<AssistantAttachment[]>([])
  const buildReply = options.buildReply ?? (async (request: AssistantReplyRequest) => {
    const response = await assistantChat(request)
    return response.reply
  })

  const activeConversation = computed(() => {
    const current = conversations.value.find((conversation) => conversation.id === activeConversationId.value)
    return current ?? conversations.value[0]!
  })

  const canRetry = computed(() => lastFailedPrompt.value.trim().length > 0 || lastFailedAttachments.value.length > 0)

  function persistState() {
    if (!storage) return
    try {
      storage.setItem(
        storageKey,
        JSON.stringify({
          activeConversationId: activeConversationId.value,
          conversations: conversations.value.map(toPersistedConversation),
        } satisfies PersistedAssistantState),
      )
    } catch {
      // Storage can be unavailable or full; chat should continue in memory.
    }
  }

  function createConversation() {
    const conversation = createEmptyConversation()
    conversations.value = [...conversations.value, conversation]
    activeConversationId.value = conversation.id
    errorMessage.value = ''
    lastFailedPrompt.value = ''
    persistState()
    return conversation
  }

  function selectConversation(id: string) {
    if (conversations.value.some((conversation) => conversation.id === id)) {
      activeConversationId.value = id
      errorMessage.value = ''
      lastFailedPrompt.value = ''
      persistState()
    }
  }

  function applyStarter(prompt: string) {
    composerText.value = prompt
  }

  function addAttachments(files: File[]) {
    const nextAttachments = files.map((file) => ({
      id: createId('attachment'),
      name: file.name,
      size: file.size,
      type: file.type,
      kind: file.type.startsWith('image/') ? 'image' : 'file',
      file,
    }) satisfies AssistantAttachment)

    composerAttachments.value = [...composerAttachments.value, ...nextAttachments]
  }

  function removeAttachment(id: string) {
    composerAttachments.value = composerAttachments.value.filter((attachment) => attachment.id !== id)
  }

  function setAssistantMode(mode: AssistantMode) {
    assistantMode.value = mode
  }

  async function sendPrompt(prompt: string, attachments: AssistantAttachment[] = composerAttachments.value) {
    if (isSending.value) {
      return
    }

    const trimmed = prompt.trim()
    if (!trimmed && attachments.length === 0) {
      return
    }

    isSending.value = true
    errorMessage.value = ''
    lastFailedPrompt.value = ''
    lastFailedAttachments.value = []

    const conversation = activeConversation.value
    const userMessage = createMessage('user', trimmed, 'done')
    userMessage.attachments = attachments.map((attachment) => ({ ...attachment }))
    const loadingMessage = createMessage('assistant', '正在思考...', 'loading')
    conversation.messages.push(userMessage, loadingMessage)
    conversation.updatedAt = Date.now()
    if (conversation.title === '新对话' && conversation.messages.length === 2) {
      conversation.title = buildConversationTitle(trimmed || attachments[0]?.name || '新对话')
    }
    conversation.summary = trimmed || `已添加 ${attachments.length} 个附件`
    persistState()

    try {
      const reply = await buildReply({
        input: trimmed || `请查看我上传的 ${attachments.length} 个附件`,
        conversationId: conversation.id,
        studyStage: currentStudyStage(),
        assistantMode: assistantMode.value,
        attachments,
      })
      const loadingIndex = conversation.messages.findIndex((message) => message.id === loadingMessage.id)
      if (loadingIndex >= 0) {
        conversation.messages.splice(loadingIndex, 1, createMessage('assistant', reply, 'done'))
      }
      composerText.value = ''
      composerAttachments.value = []
      persistState()
    } catch (error) {
      const loadingIndex = conversation.messages.findIndex((message) => message.id === loadingMessage.id)
      if (loadingIndex >= 0) {
        conversation.messages.splice(loadingIndex, 1)
      }
      errorMessage.value = error instanceof Error ? error.message : '学习助手暂时不可用'
      lastFailedPrompt.value = trimmed
      lastFailedAttachments.value = attachments.map((attachment) => ({ ...attachment }))
      persistState()
    } finally {
      isSending.value = false
      conversation.updatedAt = Date.now()
      persistState()
    }
  }

  async function sendMessage() {
    await sendPrompt(composerText.value)
  }

  async function retryLastMessage() {
    if (!lastFailedPrompt.value && lastFailedAttachments.value.length === 0) {
      return
    }
    await sendPrompt(lastFailedPrompt.value, lastFailedAttachments.value)
  }

  return {
    conversations,
    activeConversationId,
    activeConversation,
    composerText,
    composerAttachments,
    assistantMode,
    searchText,
    isSending,
    errorMessage,
    canRetry,
    applyStarter,
    addAttachments,
    removeAttachment,
    setAssistantMode,
    createConversation,
    selectConversation,
    sendMessage,
    retryLastMessage,
  }
}
