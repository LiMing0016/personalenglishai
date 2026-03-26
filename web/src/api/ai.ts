import axios from 'axios'
import { http } from './http'
import { getToken } from '@/utils/token'

export type AiIntent = 'generate' | 'rewrite' | 'explain' | 'chat'
export type AiMode = 'sm' | 'md' | 'lg'

export interface AiCommandPayload {
  apiVersion: 1
  intent: AiIntent
  mode?: AiMode
  instruction: string
  constraints?: Record<string, unknown>
  contextRefs: {
    docId: string
  }
}

export interface AiAssistantAction {
  type: string
  label: string
  text?: string
  panel?: string
}

export interface AiAssistantToolRun {
  tool: string
  status: string
  summary: string
}

export interface AiCommandResult {
  apply: string
  explain: string[]
  message: string
  status: string
  responseId?: string
  actions: AiAssistantAction[]
  toolRuns: AiAssistantToolRun[]
  replaceSelectionText?: string
}

export interface AiCommandOptions {
  signal?: AbortSignal
}

export interface AiCommandStreamEvent {
  traceId?: string
  type?: string
  status?: string
  message?: string
  toolRuns?: AiAssistantToolRun[]
}

export interface AiCommandStreamOptions extends AiCommandOptions {
  onEvent?: (event: AiCommandStreamEvent) => void
}

export type EnglishAssistantScope =
  | 'english_general'
  | 'current_draft'
  | 'assistant_output'
  | 'session_meta'
  | 'sensitive_refuse'
  | 'off_topic'
export type EnglishAssistantTaskType =
  | 'ask'
  | 'explain'
  | 'rewrite'
  | 'polish'
  | 'translate'
  | 'evaluate'
  | 'generate'

export interface EnglishAssistantChatPayload {
  conversationId: string
  message: string
  useDraftContext?: boolean
  studyStage?: string | null
  writingMode?: 'free' | 'exam' | null
  assignmentText?: string
  selectedText?: string
  draftText?: string
  preferredAction?: EnglishAssistantTaskType
}

export interface EnglishAssistantUiAction {
  type: string
  label: string
  payloadText?: string
}

export interface EnglishAssistantChatResult {
  conversationId: string
  responseId?: string
  scope: EnglishAssistantScope
  taskType: EnglishAssistantTaskType
  refused: boolean
  refusalReason?: string | null
  usedDraftContext: boolean
  message: string
  actions: EnglishAssistantUiAction[]
}

export interface EnglishAssistantStreamMetaEvent {
  type: 'meta'
  conversationId?: string
  scope?: EnglishAssistantScope
  taskType?: EnglishAssistantTaskType
  usedDraftContext?: boolean
}

export interface EnglishAssistantStreamDeltaEvent {
  type: 'delta'
  text: string
}

export interface EnglishAssistantStreamDoneEvent {
  type: 'done'
  conversationId?: string
  responseId?: string
  scope?: EnglishAssistantScope
  taskType?: EnglishAssistantTaskType
  usedDraftContext?: boolean
  response?: EnglishAssistantChatResult
}

export interface EnglishAssistantStreamErrorEvent {
  type: 'error'
  message: string
}

export type EnglishAssistantStreamEvent =
  | EnglishAssistantStreamMetaEvent
  | EnglishAssistantStreamDeltaEvent
  | EnglishAssistantStreamDoneEvent
  | EnglishAssistantStreamErrorEvent

export interface EnglishAssistantStreamOptions extends AiCommandOptions {
  onEvent?: (event: EnglishAssistantStreamEvent) => void
}

interface AiCommandSuccessResponse {
  traceId?: string
  status?: string
  result?: {
    apply?: string
    explain?: string[]
  }
  finalResult?: {
    content?: string
  }
  message?: string
  responseId?: string
  actions?: AiAssistantAction[]
  toolRuns?: AiAssistantToolRun[]
}

interface AiCommandErrorResponse {
  traceId?: string
  message?: string
  finalResult?: {
    content?: string
  }
}

interface StreamEnvelope {
  traceId?: string
  status?: string
  message?: string
  type?: string
  toolRuns?: AiAssistantToolRun[]
  response?: AiCommandSuccessResponse
}

const API_BASE_URL = '/api'

interface EnglishAssistantStreamEnvelope {
  conversationId?: string
  responseId?: string
  scope?: EnglishAssistantScope
  taskType?: EnglishAssistantTaskType
  usedDraftContext?: boolean
  response?: EnglishAssistantChatResult
  text?: string
  message?: string
}

export async function aiCommand(
  payload: AiCommandPayload,
  options: AiCommandOptions = {}
): Promise<AiCommandResult> {
  try {
    const res = await http.post<AiCommandSuccessResponse>('/ai/command', payload, {
      signal: options.signal,
      timeout: 60000,
    })
    const parsed = parseAiCommandResponse(res.data)
    if (parsed.status === 'failed') {
      throw new Error(parsed.message || 'AI request failed')
    }
    return parsed
  } catch (error) {
    throw normalizeAiError(error)
  }
}

export async function streamAiCommand(
  payload: AiCommandPayload,
  options: AiCommandStreamOptions = {}
): Promise<AiCommandResult> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}/ai/command/stream`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
    signal: options.signal,
  })

  if (!response.ok || !response.body) {
    const status = response.status
    const body = await readJsonSafely<AiCommandErrorResponse>(response)
    const message = body?.finalResult?.content || body?.message || 'AI request failed'
    const err = new Error(message)
    ;(err as Error & { status?: number }).status = status
    throw err
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'message'
  let dataLines: string[] = []
  let finalResult: AiCommandResult | null = null
  let streamError: Error | null = null

  const flushEvent = () => {
    if (!dataLines.length) {
      currentEvent = 'message'
      return
    }
    const rawData = dataLines.join('\n')
    dataLines = []

    let parsed: StreamEnvelope | null = null
    try {
      parsed = JSON.parse(rawData) as StreamEnvelope
    } catch (_) {
      currentEvent = 'message'
      return
    }

    if (currentEvent === 'assistant_event') {
      options.onEvent?.({
        traceId: parsed.traceId,
        type: parsed.type,
        status: parsed.status,
        message: parsed.message,
        toolRuns: parsed.toolRuns,
      })
    } else if (currentEvent === 'error') {
      streamError = new Error(parsed.message || 'AI stream failed')
    } else if (currentEvent === 'final' && parsed.response) {
      finalResult = parseAiCommandResponse(parsed.response)
    }

    currentEvent = 'message'
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let boundary = buffer.indexOf('\n')
      while (boundary >= 0) {
        const line = buffer.slice(0, boundary).replace(/\r$/, '')
        buffer = buffer.slice(boundary + 1)

        if (!line) {
          flushEvent()
        } else if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim() || 'message'
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }

        boundary = buffer.indexOf('\n')
      }
    }
    if (buffer.trim() || dataLines.length) {
      if (buffer.trim()) {
        const trailing = buffer.trim().replace(/\r$/, '')
        if (trailing.startsWith('data:')) {
          dataLines.push(trailing.slice(5).trimStart())
        }
      }
      flushEvent()
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      const err = new Error('AI request canceled')
      ;(err as Error & { canceled?: boolean }).canceled = true
      throw err
    }
    throw error
  } finally {
    reader.releaseLock()
  }

  if (streamError) {
    throw streamError
  }
  if (!finalResult) {
    throw new Error('AI stream ended without final result')
  }
  const resolvedFinal = finalResult as AiCommandResult
  if (resolvedFinal.status === 'failed') {
    throw new Error(resolvedFinal.message || 'AI request failed')
  }
  return resolvedFinal
}

export async function streamEnglishAssistantChat(
  payload: EnglishAssistantChatPayload,
  options: EnglishAssistantStreamOptions = {}
): Promise<EnglishAssistantChatResult> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}/english-assistant/chat/stream`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
    signal: options.signal,
  })

  if (!response.ok || !response.body) {
    const status = response.status
    const body = await readJsonSafely<{ message?: string }>(response)
    const message = body?.message || 'English assistant request failed'
    const err = new Error(message)
    ;(err as Error & { status?: number }).status = status
    throw err
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'message'
  let dataLines: string[] = []
  let finalResult: EnglishAssistantChatResult | null = null
  let streamError: Error | null = null
  let accumulatedText = ''
  let latestMeta: Partial<
    Pick<
      EnglishAssistantChatResult,
      'conversationId' | 'responseId' | 'scope' | 'taskType' | 'usedDraftContext'
    >
  > = {}

  const flushEvent = () => {
    if (!dataLines.length) {
      currentEvent = 'message'
      return
    }
    const rawData = dataLines.join('\n')
    dataLines = []

    let parsed: EnglishAssistantStreamEnvelope | null = null
    try {
      parsed = JSON.parse(rawData) as EnglishAssistantStreamEnvelope
    } catch (_) {
      currentEvent = 'message'
      return
    }

    switch (currentEvent) {
      case 'meta':
        latestMeta = {
          conversationId: parsed.conversationId,
          responseId: latestMeta.responseId,
          scope: parsed.scope,
          taskType: parsed.taskType,
          usedDraftContext: parsed.usedDraftContext,
        }
        options.onEvent?.({
          type: 'meta',
          conversationId: parsed.conversationId,
          scope: parsed.scope,
          taskType: parsed.taskType,
          usedDraftContext: parsed.usedDraftContext,
        })
        break
      case 'delta':
        const deltaText = parsed.text ?? parsed.message ?? ''
        options.onEvent?.({
          type: 'delta',
          text: deltaText,
        })
        accumulatedText += deltaText
        break
      case 'error':
        streamError = new Error(parsed.message || 'English assistant stream failed')
        options.onEvent?.({
          type: 'error',
          message: parsed.message || 'English assistant stream failed',
        })
        break
      case 'done':
        if (parsed.response) {
          if ((parsed.response.message == null || !parsed.response.message.trim()) && accumulatedText.trim()) {
            finalResult = {
              ...parsed.response,
              message: accumulatedText,
            }
          } else {
            finalResult = parsed.response
          }
        } else if (accumulatedText || latestMeta.scope || latestMeta.taskType || latestMeta.conversationId) {
          finalResult = {
            conversationId: parsed.conversationId || latestMeta.conversationId || '',
            responseId: parsed.responseId || latestMeta.responseId,
            scope: parsed.scope || latestMeta.scope || 'english_general',
            taskType: parsed.taskType || latestMeta.taskType || 'ask',
            refused: false,
            refusalReason: null,
            usedDraftContext: parsed.usedDraftContext || latestMeta.usedDraftContext || false,
            message: accumulatedText,
            actions: [],
          }
        } else if (parsed.message) {
          finalResult = {
            conversationId: parsed.conversationId || latestMeta.conversationId || '',
            responseId: parsed.responseId || latestMeta.responseId,
            scope: parsed.scope || latestMeta.scope || 'english_general',
            taskType: parsed.taskType || latestMeta.taskType || 'ask',
            refused: false,
            refusalReason: null,
            usedDraftContext: parsed.usedDraftContext || latestMeta.usedDraftContext || false,
            message: parsed.message,
            actions: [],
          }
        }
        options.onEvent?.({
          type: 'done',
          conversationId: parsed.conversationId,
          responseId: parsed.responseId,
          scope: parsed.scope,
          taskType: parsed.taskType,
          usedDraftContext: parsed.usedDraftContext,
          response: parsed.response,
        })
        break
      default:
        break
    }

    currentEvent = 'message'
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let boundary = buffer.indexOf('\n')
      while (boundary >= 0) {
        const line = buffer.slice(0, boundary).replace(/\r$/, '')
        buffer = buffer.slice(boundary + 1)

        if (!line) {
          flushEvent()
        } else if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim() || 'message'
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }

        boundary = buffer.indexOf('\n')
      }
    }
    if (buffer.trim() || dataLines.length) {
      if (buffer.trim()) {
        const trailing = buffer.trim().replace(/\r$/, '')
        if (trailing.startsWith('data:')) {
          dataLines.push(trailing.slice(5).trimStart())
        }
      }
      flushEvent()
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      const err = new Error('English assistant request canceled')
      ;(err as Error & { canceled?: boolean }).canceled = true
      throw err
    }
    throw error
  } finally {
    reader.releaseLock()
  }

  if (streamError) {
    throw streamError
  }
  const hasUsableText = (result: EnglishAssistantChatResult | null) =>
    typeof result?.message === 'string' && result.message.trim().length > 0
  if ((!finalResult && accumulatedText) || (!hasUsableText(finalResult) && accumulatedText)) {
    finalResult = {
      conversationId: latestMeta.conversationId || '',
      responseId: latestMeta.responseId,
      scope: latestMeta.scope || 'english_general',
      taskType: latestMeta.taskType || 'ask',
      refused: false,
      refusalReason: null,
      usedDraftContext: latestMeta.usedDraftContext || false,
      message: accumulatedText,
      actions: [],
    }
  }
  if (!hasUsableText(finalResult) || !finalResult) {
    finalResult = await fallbackEnglishChat(payload, options.signal, token)
  }
  if (!finalResult) {
    throw new Error('English assistant stream ended without final result')
  }
  return finalResult
}

async function fallbackEnglishChat(
  payload: EnglishAssistantChatPayload,
  signal?: AbortSignal,
  token?: string | null,
): Promise<EnglishAssistantChatResult | null> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}/english-assistant/chat`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok || !response.body) {
    return null
  }

  const raw = await readJsonSafely<{
    conversationId: string
    responseId?: string
    scope?: EnglishAssistantChatResult['scope']
    taskType?: EnglishAssistantChatResult['taskType']
    refused?: boolean
    refusalReason?: string | null
    usedDraftContext?: boolean
    message?: string
    actions?: EnglishAssistantUiAction[]
  }>(response)

  if (!raw) {
    return null
  }
  if (raw.conversationId == null || raw.scope == null || raw.taskType == null || raw.usedDraftContext == null) {
    return null
  }
  return {
    conversationId: raw.conversationId,
    responseId: raw.responseId,
    scope: raw.scope,
    taskType: raw.taskType,
    refused: Boolean(raw.refused),
    refusalReason: raw.refusalReason,
    usedDraftContext: raw.usedDraftContext,
    message: raw.message || '',
    actions: raw.actions ?? [],
  }
}

function parseAiCommandResponse(data: AiCommandSuccessResponse): AiCommandResult {
  const applyRaw = data.result?.apply ?? data.finalResult?.content ?? data.message ?? ''
  const explain = Array.isArray(data.result?.explain) ? data.result!.explain! : []
  const message = (data.message ?? applyRaw ?? '').trim()
  const actions = Array.isArray(data.actions) ? data.actions : []
  const toolRuns = Array.isArray(data.toolRuns) ? data.toolRuns : []
  let apply = message || applyRaw
  let replaceSelectionText = resolveReplaceSelectionText(actions)

  if (!replaceSelectionText) {
    const legacy = parseLegacyPayload(applyRaw)
    if (legacy.apply) {
      apply = legacy.apply
    }
    replaceSelectionText = legacy.replaceSelectionText
  }

  return {
    apply,
    explain,
    message: message || apply,
    status: data.status || 'completed',
    responseId: data.responseId,
    actions,
    toolRuns,
    replaceSelectionText,
  }
}

function resolveReplaceSelectionText(actions: AiAssistantAction[]): string | undefined {
  const replaceAction = actions.find((action) => action?.type === 'replace_selection')
  const text = replaceAction?.text?.trim()
  return text ? text : undefined
}

function parseLegacyPayload(applyRaw: string): { apply?: string; replaceSelectionText?: string } {
  try {
    const parsed = JSON.parse(applyRaw) as {
      mode?: string
      content?: { primary_text?: string }
      actions?: Array<{ type?: string; text?: string | null }>
    }
    const primaryText = parsed?.content?.primary_text
    const replaceAction = parsed?.actions?.find(action => action?.type === 'replace_selection')
    return {
      apply: typeof primaryText === 'string' && primaryText.trim() !== '' ? primaryText.trim() : undefined,
      replaceSelectionText:
        replaceAction && typeof replaceAction.text === 'string' && replaceAction.text.trim() !== ''
          ? replaceAction.text.trim()
          : undefined,
    }
  } catch (_) {
    return {}
  }
}

async function readJsonSafely<T>(response: Response): Promise<T | null> {
  try {
    return (await response.json()) as T
  } catch (_) {
    return null
  }
}

function normalizeAiError(error: unknown): unknown {
  if (axios.isAxiosError(error) && error.code === 'ERR_CANCELED') {
    const err = new Error('AI request canceled')
    ;(err as Error & { canceled?: boolean }).canceled = true
    return err
  }
  if (axios.isAxiosError<AiCommandErrorResponse>(error)) {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.finalResult?.content || body?.message || 'AI request failed'
    const err = new Error(message)
    ;(err as Error & { status?: number }).status = status
    return err
  }
  return error
}
