import { computed, ref } from 'vue'

import {
  assistantApi,
  assistantChatStream,
  type AssistantChatResult,
  type AssistantChatStreamHandlers,
  type AssistantConversationDto,
  type AssistantProjectDto,
} from '../../api/assistant.ts'
import { normalizeAssistantBlocks } from '../../types/assistantBlocks.ts'
import { stageCache } from '../../stores/stageCache.ts'
import { showToast } from '../../utils/toast.ts'
import {
  createAttachmentFile,
  createAttachmentMetadata,
  createBrowserAssistantAttachmentBlobStore,
  type AssistantAttachmentBlobStore,
  type StoredAssistantAttachment,
} from './assistantAttachmentStore.ts'
import { type AssistantAttachmentSource, validateAssistantFiles } from './assistantAttachmentRules.ts'
import { findRetryUserMessage } from './assistantMessageActions.ts'
import type { AssistantInteractionContext, AssistantSelection } from '../../types/assistantRequest.ts'
import {
  mergeRemoteConversationListWithTransientAttachments,
  mergeTransientMessageAttachments,
} from './assistantConversationMerge.ts'
import {
  type AssistantAttachment,
  type AssistantAttachmentMetadata,
  type AssistantMode,
  type AssistantReplyResult,
  type AssistantReplyRequest,
  type AssistantConversation,
  type AssistantMessage,
} from './assistantMock.ts'

type AssistantReplyValue = string | AssistantReplyResult | AssistantChatResult

interface CreateAssistantStateOptions {
  buildReply?: (request: AssistantReplyRequest, stream?: AssistantChatStreamHandlers) => Promise<AssistantReplyValue>
  storage?: Storage
  storageKey?: string
  remote?: boolean
  attachmentStore?: AssistantAttachmentBlobStore
}

const DEFAULT_STORAGE_KEY = 'peai:assistant:state:v1'

interface PersistedAssistantMessage {
  id: string
  role: AssistantMessage['role']
  content: string
  status: AssistantMessage['status']
  parts?: unknown
  attachments?: AssistantAttachmentMetadata[]
}

interface PersistedAssistantConversation {
  id: string
  projectId?: number | null
  title: string
  summary: string
  createdAt?: number
  updatedAt: number
  pinned?: boolean
  archived?: boolean
  messages: PersistedAssistantMessage[]
}

interface PersistedAssistantState {
  activeConversationId: string
  conversations: PersistedAssistantConversation[]
}

interface RestoredAssistantState {
  activeConversationId: string
  conversations: AssistantConversation[]
}

function createId(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`
}

function createEmptyConversation(): AssistantConversation {
  const now = Date.now()
  return {
    id: createId('conv'),
    projectId: null,
    title: '新对话',
    summary: '',
    createdAt: now,
    updatedAt: now,
    pinned: false,
    archived: false,
    messages: [],
  }
}

function createMessage(
  role: AssistantMessage['role'],
  content: string,
  status: AssistantMessage['status'],
  parts?: unknown,
): AssistantMessage {
  const normalizedParts = normalizeAssistantBlocks(parts)
  return {
    id: createId('msg'),
    role,
    content,
    status,
    parts: normalizedParts.length ? normalizedParts : undefined,
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
    createdAt: conversation.createdAt,
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
        parts: message.parts?.length ? message.parts : undefined,
        attachments: persistedAttachmentMetadata(message),
      })),
  }
}

function persistedAttachmentMetadata(message: AssistantMessage): AssistantAttachmentMetadata[] | undefined {
  const metadata = message.attachments?.length
    ? message.attachments.map(createAttachmentMetadata)
    : message.attachmentMetadata
  return metadata?.length ? metadata.map((attachment) => ({ ...attachment })) : undefined
}

function isPersistedRole(role: unknown): role is AssistantMessage['role'] {
  return role === 'user' || role === 'assistant'
}

function isPersistedAttachmentMetadata(value: unknown): value is AssistantAttachmentMetadata {
  const candidate = value as Partial<AssistantAttachmentMetadata> | null
  return Boolean(
    candidate &&
      typeof candidate.id === 'string' &&
      typeof candidate.name === 'string' &&
      typeof candidate.size === 'number' &&
      typeof candidate.type === 'string' &&
      (candidate.kind === 'image' || candidate.kind === 'file'),
  )
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
            parts: normalizeAssistantBlocks(candidate.parts),
            attachmentMetadata: Array.isArray(candidate.attachments)
              ? candidate.attachments.filter(isPersistedAttachmentMetadata)
              : undefined,
          } satisfies AssistantMessage
        })
        .filter((message): message is AssistantMessage => Boolean(message))
    : []

  const updatedAt = typeof conversation.updatedAt === 'number' ? conversation.updatedAt : Date.now()
  const createdAt = typeof conversation.createdAt === 'number' ? conversation.createdAt : updatedAt

  return {
    id: conversation.id,
    projectId: typeof conversation.projectId === 'number' ? conversation.projectId : null,
    title: typeof conversation.title === 'string' && conversation.title.trim() ? conversation.title : '新对话',
    summary: typeof conversation.summary === 'string' ? conversation.summary : '',
    createdAt,
    updatedAt,
    pinned: Boolean(conversation.pinned),
    archived: Boolean(conversation.archived),
    messages,
  }
}

function parseRemoteTime(value: string | null | undefined, fallback: number) {
  if (!value) return fallback
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? fallback : parsed
}

function fromRemoteConversation(dto: AssistantConversationDto): AssistantConversation {
  const now = Date.now()
  const createdAt = parseRemoteTime(dto.createdAt, now)
  return {
    id: dto.id,
    projectId: dto.projectId ?? null,
    title: dto.title || '新对话',
    summary: dto.summary ?? '',
    createdAt,
    updatedAt: parseRemoteTime(dto.updatedAt, createdAt),
    pinned: dto.pinned,
    archived: dto.archived,
    messages: (dto.messages ?? []).map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      status: message.status === 'failed' ? 'done' : 'done',
      parts: normalizeAssistantBlocks(message.parts),
    })),
  }
}

function restoreAssistantState(storage: Storage | undefined, storageKey: string): RestoredAssistantState | null {
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
  const attachmentStore = options.attachmentStore ?? createBrowserAssistantAttachmentBlobStore()
  const remoteConversationIds = new Set<string>()
  const restored = restoreAssistantState(storage, storageKey)
  const conversations = ref<AssistantConversation[]>(restored?.conversations ?? [createEmptyConversation()])
  const activeConversationId = ref(restored?.activeConversationId ?? conversations.value[0]!.id)
  const projects = ref<AssistantProjectDto[]>([])
  const archivedConversations = ref<AssistantConversation[]>([])
  const isLoadingConversations = ref(false)
  const composerText = ref('')
  const composerAttachments = ref<AssistantAttachment[]>([])
  const pendingSelection = ref<AssistantSelection | null>(null)
  const assistantMode = ref<AssistantMode>('default')
  const searchText = ref('')
  const isSending = ref(false)
  const errorMessage = ref('')
  const lastFailedPrompt = ref('')
  const lastFailedAttachments = ref<AssistantAttachment[]>([])
  const lastFailedInteraction = ref<AssistantInteractionContext>()
  const buildReply = options.buildReply ?? (async (request: AssistantReplyRequest, stream?: AssistantChatStreamHandlers) => {
    const response = await assistantChatStream(request, stream)
    return response.reply
  })

  const activeConversation = computed(() => {
    const current = conversations.value.find((conversation) => conversation.id === activeConversationId.value)
      ?? archivedConversations.value.find((conversation) => conversation.id === activeConversationId.value)
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
    const previous = conversations.value.find((conversation) => conversation.id === id)
    const merged = mergeTransientMessageAttachments(previous, next)
    conversations.value = conversations.value.map((conversation) => (conversation.id === id ? merged : conversation))
    if (activeConversationId.value === id) {
      activeConversationId.value = merged.id
    }
    persistState()
    void hydrateConversationAttachments(merged.id).catch(() => undefined)
  }

  function replaceArchivedConversation(id: string, next: AssistantConversation) {
    const previous = archivedConversations.value.find((conversation) => conversation.id === id)
    const merged = mergeTransientMessageAttachments(previous, next)
    archivedConversations.value = archivedConversations.value.map((conversation) => (
      conversation.id === id ? merged : conversation
    ))
    if (activeConversationId.value === id) {
      activeConversationId.value = merged.id
    }
    void hydrateConversationAttachments(merged.id).catch(() => undefined)
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
      const nextRemoteConversations = remoteConversations.map((conversation) => {
        remoteConversationIds.add(conversation.id)
        return fromRemoteConversation(conversation)
      })
      const nextArchivedConversations = remoteArchived.map((conversation) => {
        remoteConversationIds.add(conversation.id)
        return fromRemoteConversation(conversation)
      })
      const nextConversations = mergeRemoteConversationListWithTransientAttachments(
        conversations.value,
        nextRemoteConversations,
      )
      archivedConversations.value = mergeRemoteConversationListWithTransientAttachments(
        [...conversations.value, ...archivedConversations.value],
        nextArchivedConversations,
      )
      conversations.value = nextConversations.length > 0 ? nextConversations : [createEmptyConversation()]
      activeConversationId.value = conversations.value[0]!.id
      void hydrateAllConversationAttachments().catch(() => undefined)
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
    return conversation
  }

  async function selectConversation(id: string) {
    const isVisibleConversation = conversations.value.some((conversation) => conversation.id === id)
    const isArchivedConversation = archivedConversations.value.some((conversation) => conversation.id === id)
    if (isVisibleConversation || isArchivedConversation) {
      activeConversationId.value = id
      errorMessage.value = ''
      lastFailedPrompt.value = ''
      persistState()
      if (remote && remoteConversationIds.has(id)) {
        try {
          const remoteConversation = fromRemoteConversation(await assistantApi.getConversation(id))
          if (isArchivedConversation) {
            replaceArchivedConversation(id, remoteConversation)
          } else {
            replaceConversation(id, remoteConversation)
          }
        } catch (error) {
          errorMessage.value = error instanceof Error ? error.message : '对话加载失败'
        }
      }
    }
  }

  function applyStarter(prompt: string) {
    composerText.value = prompt
  }

  function setPendingSelection(selection: AssistantSelection | null) {
    pendingSelection.value = selection
      ? {
          text: selection.text,
          source: selection.source,
          sourceId: selection.sourceId,
          messageId: selection.messageId,
          documentId: selection.documentId,
          range: selection.range,
        }
      : null
  }

  function addAttachments(files: File[], source: AssistantAttachmentSource = 'picker') {
    const validation = validateAssistantFiles(files, composerAttachments.value, source)
    if (validation.rejected.length > 0) {
      showToast(validation.rejected[0]!.reason, 'error')
    }

    const nextAttachments = validation.accepted.map((file) => ({
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

  async function sendPrompt(
    prompt: string,
    attachments: AssistantAttachment[] = composerAttachments.value,
    interaction?: AssistantInteractionContext,
  ) {
    if (isSending.value) {
      return
    }

    const trimmed = prompt.trim()
    const selectionForRequest = pendingSelection.value
    if (!trimmed && attachments.length === 0 && !selectionForRequest) {
      return
    }

    isSending.value = true
    errorMessage.value = ''
    lastFailedPrompt.value = ''
    lastFailedAttachments.value = []
    lastFailedInteraction.value = undefined

    let conversation = activeConversation.value
    try {
      conversation = await ensureRemoteConversation(conversation)
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : '新建远程对话失败'
      isSending.value = false
      return
    }
    const userMessage = createMessage('user', trimmed, 'done')
    userMessage.interaction = interaction
    userMessage.attachments = attachments.map((attachment) => ({ ...attachment }))
    userMessage.attachmentMetadata = attachments.map(createAttachmentMetadata)
    const loadingMessage = createMessage('assistant', '正在思考...', 'loading')
    conversation.messages.push(userMessage, loadingMessage)
    conversation.updatedAt = Date.now()
    if (conversation.title === '新对话' && conversation.messages.length === 2) {
      conversation.title = buildConversationTitle(trimmed || attachments[0]?.name || '新对话')
    }
    conversation.summary = trimmed || `已添加 ${attachments.length} 个附件`
    persistState()
    if (attachments.length > 0) {
      try {
        await saveAttachmentBlobs(attachments)
      } catch {
        showToast('附件本地保存失败，刷新后可能无法恢复', 'error')
      }
    }

    composerText.value = ''
    composerAttachments.value = []
    setPendingSelection(null)

    try {
      let streamedReply = ''
      const updateLoadingMessage = (content: string, parts?: unknown) => {
        const loadingIndex = conversation.messages.findIndex((message) => message.id === loadingMessage.id)
        if (loadingIndex >= 0) {
          const normalizedParts = normalizeAssistantBlocks(parts)
          conversation.messages.splice(loadingIndex, 1, {
            ...conversation.messages[loadingIndex]!,
            content,
            parts: normalizedParts.length ? normalizedParts : conversation.messages[loadingIndex]!.parts,
          })
        }
      }

      const replyResult = await buildReply({
        input: trimmed || `请查看我上传的 ${attachments.length} 个附件`,
        conversationId: conversation.id,
        studyStage: currentStudyStage(),
        assistantMode: assistantMode.value,
        intent: selectionForRequest ? 'explain' : 'free_chat',
        scope: selectionForRequest
          ? (trimmed ? 'selection_and_message' : 'selection')
          : 'message_only',
        selection: selectionForRequest ?? undefined,
        interaction,
        attachments,
      }, {
        onDelta: (delta) => {
          streamedReply += delta
          updateLoadingMessage(streamedReply)
        },
        onCompleted: (content, parts) => {
          streamedReply = content
          updateLoadingMessage(content, parts)
        },
      })
      const reply = typeof replyResult === 'string' ? replyResult : replyResult.reply
      const replyParts = typeof replyResult === 'string' ? [] : normalizeAssistantBlocks(replyResult.parts)
      const loadingIndex = conversation.messages.findIndex((message) => message.id === loadingMessage.id)
      if (loadingIndex >= 0) {
        conversation.messages.splice(loadingIndex, 1, createMessage('assistant', reply, 'done', replyParts))
      }
      if (remote && remoteConversationIds.has(conversation.id)) {
        try {
          replaceConversation(conversation.id, fromRemoteConversation(await assistantApi.getConversation(conversation.id)))
        } catch {
          // Local optimistic state is already usable; the next selection reloads from server.
        }
      }
      persistState()
    } catch (error) {
      conversation.messages = conversation.messages.filter(
        (message) => message.id !== userMessage.id && message.id !== loadingMessage.id,
      )
      if (attachments.length > 0) {
        void attachmentStore.deleteMany(attachments.map((attachment) => attachment.id)).catch(() => undefined)
      }
      errorMessage.value = error instanceof Error ? error.message : '学习助手暂时不可用'
      lastFailedPrompt.value = trimmed
      lastFailedAttachments.value = attachments.map((attachment) => ({ ...attachment }))
      lastFailedInteraction.value = interaction
      persistState()
    } finally {
      isSending.value = false
      if (selectionForRequest) {
        setPendingSelection(null)
      }
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
    await sendPrompt(lastFailedPrompt.value, lastFailedAttachments.value, lastFailedInteraction.value)
  }

  async function retryAssistantMessage(messageId: string) {
    const retryMessage = findRetryUserMessage(activeConversation.value.messages, messageId)
    if (!retryMessage) {
      throw new Error('没有找到可重试的上一条用户消息')
    }
    await sendPrompt(retryMessage.content, retryMessage.attachments ?? [], retryMessage.interaction)
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
    const conversation = conversations.value.find((item) => item.id === id) ??
      archivedConversations.value.find((item) => item.id === id)
    if (remote && remoteConversationIds.has(id)) {
      await assistantApi.deleteConversation(id)
    }
    if (conversation) {
      await deleteConversationAttachments(conversation)
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

  async function saveAttachmentBlobs(attachments: AssistantAttachment[]) {
    await Promise.all(
      attachments.map((attachment) =>
        attachmentStore.put({
          ...createAttachmentMetadata(attachment),
          blob: attachment.file,
          createdAt: Date.now(),
        }),
      ),
    )
  }

  async function hydrateConversation(conversation: AssistantConversation): Promise<AssistantConversation> {
    const messages = await Promise.all(
      conversation.messages.map(async (message) => {
        const metadata = persistedAttachmentMetadata(message)
        if (!metadata?.length || message.attachments?.length) {
          return metadata?.length ? { ...message, attachmentMetadata: metadata } : message
        }

        const storedRecords = await Promise.all(metadata.map((attachment) => attachmentStore.get(attachment.id)))
        const attachments = storedRecords
          .map((record, index) => recordToAttachment(record, metadata[index]))
          .filter((attachment): attachment is AssistantAttachment => Boolean(attachment))

        return attachments.length > 0
          ? { ...message, attachments, attachmentMetadata: metadata }
          : { ...message, attachmentMetadata: metadata }
      }),
    )
    return { ...conversation, messages }
  }

  function recordToAttachment(
    record: StoredAssistantAttachment | null,
    metadata: AssistantAttachmentMetadata | undefined,
  ): AssistantAttachment | null {
    if (!record || !metadata) return null
    return createAttachmentFile(metadata, record.blob)
  }

  async function hydrateConversationAttachments(id: string) {
    const conversation = conversations.value.find((item) => item.id === id)
    if (!conversation) return
    const hydrated = await hydrateConversation(conversation)
    conversations.value = conversations.value.map((item) => (item.id === id ? hydrated : item))
    persistState()
  }

  async function hydrateAllConversationAttachments() {
    conversations.value = await Promise.all(conversations.value.map(hydrateConversation))
    archivedConversations.value = await Promise.all(archivedConversations.value.map(hydrateConversation))
    persistState()
  }

  async function deleteConversationAttachments(conversation: AssistantConversation) {
    const attachmentIds = conversation.messages.flatMap((message) =>
      persistedAttachmentMetadata(message)?.map((attachment) => attachment.id) ?? [],
    )
    await attachmentStore.deleteMany([...new Set(attachmentIds)])
  }

  void hydrateAllConversationAttachments().catch(() => undefined)

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
    pendingSelection,
    isSending,
    errorMessage,
    canRetry,
    loadRemoteState,
    applyStarter,
    setPendingSelection,
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
    sendPrompt,
    retryLastMessage,
    retryAssistantMessage,
  }
}
