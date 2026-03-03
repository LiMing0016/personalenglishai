import axios from 'axios'
import { http } from './http'

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

export interface AiCommandResult {
  apply: string
  explain: string[]
  replaceSelectionText?: string
}

export interface AiCommandOptions {
  signal?: AbortSignal
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
}

interface AiCommandErrorResponse {
  traceId?: string
  message?: string
  finalResult?: {
    content?: string
  }
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
    const data = res.data
    const applyRaw = data.result?.apply ?? data.finalResult?.content ?? ''
    const explain = Array.isArray(data.result?.explain) ? data.result!.explain! : []
    let apply = applyRaw
    let replaceSelectionText: string | undefined

    try {
      const parsed = JSON.parse(applyRaw) as {
        mode?: string
        content?: { primary_text?: string }
        actions?: Array<{ type?: string; text?: string | null }>
      }
      const primaryText = parsed?.content?.primary_text
      if (typeof primaryText === 'string' && primaryText.trim() !== '') {
        apply = primaryText.trim()
      }
      const replaceAction = parsed?.actions?.find(a => a?.type === 'replace_selection')
      if (replaceAction && typeof replaceAction.text === 'string' && replaceAction.text.trim() !== '') {
        replaceSelectionText = replaceAction.text.trim()
      }
    } catch (_) {
      // Keep backward compatibility when apply is plain text or legacy JSON shape.
    }

    return { apply, explain, replaceSelectionText }
  } catch (error) {
    if (axios.isAxiosError(error) && error.code === 'ERR_CANCELED') {
      const err = new Error('AI request canceled')
      ;(err as Error & { canceled?: boolean }).canceled = true
      throw err
    }
    if (axios.isAxiosError<AiCommandErrorResponse>(error)) {
      const status = error.response?.status
      const body = error.response?.data
      const message = body?.finalResult?.content || body?.message || 'AI request failed'
      const err = new Error(message)
      ;(err as Error & { status?: number }).status = status
      throw err
    }
    throw error
  }
}
