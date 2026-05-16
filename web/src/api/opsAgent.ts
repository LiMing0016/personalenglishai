import { http } from './http'

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface AgentDebugRun {
  runId: string
  traceId?: string | null
  userId?: number | null
  conversationId?: string | null
  rawUserMessage?: string | null
  intent?: string | null
  routeType?: string | null
  workflow?: string | null
  targetAgent?: string | null
  agentName?: string | null
  model?: string | null
  status?: string | null
  latencyMs?: number | null
  totalTokens?: number | null
  errorMessage?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface AgentDebugStep {
  id?: number
  runId?: string
  stepOrder?: number
  stepType?: string
  agentName?: string | null
  inputJson?: string | null
  outputJson?: string | null
  usageJson?: string | null
  errorMessage?: string | null
  createdAt?: string | null
}

export interface AgentPromptSnapshot {
  id?: number
  runId?: string
  promptKey?: string | null
  promptVersion?: string | null
  promptHash?: string | null
  agentName?: string | null
  model?: string | null
  systemPrompt?: string | null
  developerPrompt?: string | null
  userPrompt?: string | null
  variablesJson?: string | null
  createdAt?: string | null
}

export interface AgentDebugRunDetail extends AgentDebugRun {
  routeRequest?: Record<string, unknown>
  routingDecision?: Record<string, unknown>
  usage?: Record<string, unknown>
  output?: Record<string, unknown>
  steps?: AgentDebugStep[]
  prompts?: AgentPromptSnapshot[]
}

export interface AgentRunQuery {
  status?: string
  intent?: string
  targetAgent?: string
  model?: string
  userId?: string
  conversationId?: string
  createdFrom?: string
  createdTo?: string
  page?: number
  size?: number
}

export interface PromptSnapshotQuery {
  promptKey?: string
  promptHash?: string
  agentName?: string
  model?: string
  createdFrom?: string
  createdTo?: string
  page?: number
  size?: number
}

function cleanParams(params: object): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}

export const opsAgentApi = {
  async listRuns(params: AgentRunQuery = {}): Promise<PageResponse<AgentDebugRun>> {
    const res = await http.get<PageResponse<AgentDebugRun>>('/ops/agent/runs', {
      params: cleanParams(params),
    })
    return res.data
  },

  async getRun(runId: string): Promise<AgentDebugRunDetail> {
    const res = await http.get<AgentDebugRunDetail>(`/ops/agent/runs/${encodeURIComponent(runId)}`)
    return res.data
  },

  async listPrompts(params: PromptSnapshotQuery = {}): Promise<PageResponse<AgentPromptSnapshot>> {
    const res = await http.get<PageResponse<AgentPromptSnapshot>>('/ops/agent/prompts', {
      params: cleanParams(params),
    })
    return res.data
  },
}
