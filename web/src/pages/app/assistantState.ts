import { computed, ref } from 'vue'

import { assistantApi, assistantChat, type AssistantConversationDto, type AssistantProjectDto } from '../../api/assistant.ts'
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
  remote?: boolean
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
  projectId?: number | null
  title: string
  summary: string
  updatedAt: number
  pinned?: boolean
  archived?: boolean
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
    projectId: null,
    title: '新对话',
    summary: '',
    updatedAt: Date.now(),
    pinned: false,
    archived: false,
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
    projectId: conversation.projectId ?? null,
    title: conversation.title,
    summary: conversation.summary,
    updatedAt: conversation.updatedAt,
    pinned: Boolean(conversation.pinned),
    archived: Boolean(conversation.archived),
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
    projectId: typeof conversation.projectId === 'number' ? conversation.projectId : null,
    title: typeof conversation.title === 'string' && conversation.title.trim() ? conversation.title : '新对话',
    summary: typeof conversation.summary === 'string' ? conversation.summary : '',
    updatedAt: typeof conversation.updatedAt === 'number' ? conversation.updatedAt : Date.now(),
    pinned: Boolean(conversation.pinned),
    archived: Boolean(conversation.archived),
    messages,
  }
}

function parseRemoteTime(value: string | null | undefined) {
  if (!value) return Date.now()
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? Date.now() : parsed
}

function fromRemoteConversation(dto: AssistantConversationDto): AssistantConversation {
  return {
    id: dto.id,
    projectId: dto.projectId ?? null,
    title: dto.title || '新对话',
    summary: dto.summary ?? '',
    updatedAt: parseRemoteTime(dto.updatedAt ?? dto.createdAt),
    pinned: dto.pinned,
    archived: dto.archived,
    messages: (dto.messages ?? []).map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      status: message.status === 'failed' ? 'done' : 'done',
    })),
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
  const remote = Boolean(options.remote)
  const remoteConversationIds = new Set<string>()
  const restored = restoreAssistantState(storage, storageKey)
  const conversations = ref<AssistantConversation[]>(restored?.conversations ?? [createEmptyConversation()])
  const activeConversationId = ref(restored?.activeConversationId ?? conversations.value[0]!.id)
  const projects = ref<AssistantProjectDto[]>([])
  const archivedConversations = ref<AssistantConversation[]>([])
  const isLoadingConversations = ref(false)
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

  function replaceConversation(id: string, next: AssistantConversation) {
    conversations.value = conversations.value.map((conversation) => (conversation.id === id ? next : conversation))
    if (activeConversationId.value === id) {
      activeConversationId.value = next.id
    }
    persistState()
  }

  function removeConversationLocal(id: string) {
    conversations.value = conversations.value.filter((conversation) => conversation.id !== id)
    archivedConversations.value = archivedConversations.value.filter((conversation) => conversation.id !== id)
    if (conversations.value.length === 0) {
      conversations.value = [createEmptyConversation()]
    }
    if (!conversations.value.some((conversation) => conversation.id === activeConversationId.value)) {
      activeConversationId.value = conversations.value[0]!.id
    }
    persistState()
  }

  async function loadRemoteState() {
    if (!remote) return
    isLoadingConversations.value = true
    try {
      const [remoteProjects, remoteConversations, remoteArchived] = await Promise.all([
        assistantApi.listProjects(),
        assistantApi.listConversations({ archived: false }),
        assistantApi.listConversations({ archived: true }),
      ])
      projects.value = remoteProjects
      remoteConversationIds.clear()
      const nextConversations = remoteConversations.map((conversation) => {
        remoteConversationIds.add(conversation.id)
        return fromRemoteConversation(conversation)
      })
      archivedConversations.value = remoteArchived.map((conversation) => {
        remoteConversationIds.add(conversation.id)
        return fromRemoteConversation(conversation)
      })
      conversations.value = nextConversations.length > 0 ? nextConversations : [createEmptyConversation()]
      activeConversationId.value = conversations.value[0]!.id
      if (remoteConversationIds.has(activeConversationId.value)) {
        const detail = await assistantApi.getConversation(activeConversationId.value)
        replaceConversation(activeConversationId.value, fromRemoteConversation(detail))
      }
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '历史对话加载失败'
    } finally {
      isLoadingConversations.value = false
      persistState()
    }
  }

  async function ensureRemoteConversation(conversation: AssistantConversation) {
    if (!remote || remoteConversationIds.has(conversation.id)) {
      return conversation
    }
    const remoteConversation = await assistantApi.createConversation({
      title: conversation.title === '新对话' ? undefined : conversation.title,
      projectId: conversation.projectId ?? null,
    })
    const next = fromRemoteConversation(remoteConversation)
    remoteConversationIds.add(next.id)
    replaceConversation(conversation.id, next)
    return next
  }

  function createConversation() {
    const conversation = createEmptyConversation()
    conversations.value = [...conversations.value, conversation]
    activeConversationId.value = conversation.id
    errorMessage.value = ''
    lastFailedPrompt.value = ''
    persistState()
    if (remote) {
      void ensureRemoteConversation(conversation).catch((error) => {
        errorMessage.value = error instanceof Error ? error.message : '新建对话失败'
      })
    }
    return conversation
  }

  async function selectConversation(id: string) {
    if (conversations.value.some((conversation) => conversation.id === id)) {
      activeConversationId.value = id
      errorMessage.value = ''
      lastFailedPrompt.value = ''
      persistState()
      if (remote && remoteConversationIds.has(id)) {
        try {
          replaceConversation(id, fromRemoteConversation(await assistantApi.getConversation(id)))
        } catch (error) {
          errorMessage.value = error instanceof Error ? error.message : '对话加载失败'
        }
      }
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

    let conversation = activeConversation.value
    try {
      conversation = await ensureRemoteConversation(conversation)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '新建远程对话失败'
      isSending.value = false
      return
    }
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
      if (remote && remoteConversationIds.has(conversation.id)) {
        try {
          replaceConversation(conversation.id, fromRemoteConversation(await assistantApi.getConversation(conversation.id)))
        } catch {
          // Local optimistic state is already usable; the next selection reloads from server.
        }
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

  async function renameConversation(id: string, title: string) {
    const conversation = conversations.value.find((item) => item.id === id)
    if (!conversation) return
    const nextTitle = title.trim()
    if (!nextTitle) return
    if (remote && remoteConversationIds.has(id)) {
      replaceConversation(id, fromRemoteConversation(await assistantApi.updateConversation(id, {
        title: nextTitle,
        summary: conversation.summary,
      })))
      return
    }
    conversation.title = nextTitle
    persistState()
  }

  async function setConversationPinned(id: string, pinned: boolean) {
    const conversation = conversations.value.find((item) => item.id === id)
    if (!conversation) return
    if (remote && remoteConversationIds.has(id)) {
      replaceConversation(id, fromRemoteConversation(await assistantApi.setPinned(id, pinned)))
      return
    }
    conversation.pinned = pinned
    persistState()
  }

  async function archiveConversation(id: string) {
    const conversation = conversations.value.find((item) => item.id === id)
    if (!conversation) return
    if (remote && remoteConversationIds.has(id)) {
      const archived = fromRemoteConversation(await assistantApi.archiveConversation(id))
      archivedConversations.value = [archived, ...archivedConversations.value.filter((item) => item.id !== id)]
    }
    removeConversationLocal(id)
  }

  async function restoreConversation(id: string) {
    const conversation = archivedConversations.value.find((item) => item.id === id)
    if (!conversation) return
    if (remote && remoteConversationIds.has(id)) {
      const restored = fromRemoteConversation(await assistantApi.restoreConversation(id))
      archivedConversations.value = archivedConversations.value.filter((item) => item.id !== id)
      conversations.value = [restored, ...conversations.value.filter((item) => item.id !== id)]
      activeConversationId.value = restored.id
      persistState()
    }
  }

  async function deleteConversation(id: string) {
    if (remote && remoteConversationIds.has(id)) {
      await assistantApi.deleteConversation(id)
    }
    remoteConversationIds.delete(id)
    removeConversationLocal(id)
  }

  async function moveConversation(id: string, projectId: number | null) {
    const conversation = conversations.value.find((item) => item.id === id)
    if (!conversation) return
    if (remote && remoteConversationIds.has(id)) {
      replaceConversation(id, fromRemoteConversation(await assistantApi.moveConversation(id, projectId)))
      return
    }
    conversation.projectId = projectId
    persistState()
  }

  async function shareConversation(id: string) {
    const conversation = conversations.value.find((item) => item.id === id)
    if (!conversation) {
      throw new Error('对话不存在')
    }
    const remoteConversation = await ensureRemoteConversation(conversation)
    return assistantApi.shareConversation(remoteConversation.id)
  }

  async function createProject(name: string) {
    const project = await assistantApi.createProject(name)
    projects.value = [project, ...projects.value]
    return project
  }

  return {
    conversations,
    archivedConversations,
    projects,
    activeConversationId,
    activeConversation,
    isLoadingConversations,
    composerText,
    composerAttachments,
    assistantMode,
    searchText,
    isSending,
    errorMessage,
    canRetry,
    loadRemoteState,
    applyStarter,
    addAttachments,
    removeAttachment,
    setAssistantMode,
    createConversation,
    selectConversation,
    renameConversation,
    setConversationPinned,
    archiveConversation,
    restoreConversation,
    deleteConversation,
    moveConversation,
    shareConversation,
    createProject,
    sendMessage,
    retryLastMessage,
  }
}
