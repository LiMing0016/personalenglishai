import { http } from './http'
import { getToken } from '@/utils/token'

export interface AdminMe {
  userId: number
  email: string | null
  nickname: string | null
  roles: string[]
  permissions: string[]
}

export interface AdminPageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface AdminUserListItem {
  id: number
  email: string | null
  phone: string | null
  nickname: string
  status: string
  registerSource: string | null
  studyStage: string | null
  role: string
  adminRoles: string[]
  lastActiveAt: string | null
  createdAt: string | null
}

export interface AdminUserDetail {
  id: number
  email: string | null
  phone: string | null
  nickname: string
  avatarUrl: string | null
  status: string
  registerSource: string | null
  createdAt: string | null
  lastActiveAt: string | null
  role: string
  adminRoles: string[]
  studyStage: string | null
  aiMode: number | null
  ability: Record<string, unknown>
  stats: Record<string, unknown>
  recentEvaluations: any[]
}

export interface AdminEssayListItem {
  evaluationId: number
  userId: number
  userNickname: string
  mode: string
  taskPromptPreview: string
  essayPreview: string
  overallScore: number | null
  gaokaoScore: number | null
  band: string | null
  favorited: boolean
  archived: boolean
  createdAt: string
}

export interface AdminEssayDetail {
  evaluationId: number
  mode: string
  taskPrompt: string | null
  essayText: string
  createdAt: string
  documentId?: number | null
  requestId?: string | null
  result?: any
  user: { id: number; nickname: string; email: string | null }
  taskStatus?: string
  taskError?: string | null
  submittedAt?: number | null
  completedAt?: number | null
}

export interface AdminPromptDto {
  id?: number
  stageId: number | null
  paper: string
  title: string
  promptText: string
  examYear: number | null
  imageUrl: string | null
  imageDescription: string | null
  materialText: string | null
  task: string | null
  wordCountMin: number | null
  wordCountMax: number | null
  maxScore: number | null
  source: string | null
  isActive: number
}

export interface AdminRubricDimension {
  mode: string
  dimensionKey: string
  displayName: string
  sortOrder: number
  levels: Array<{ level: string; score: number; criteria: string }>
}

export interface AdminRubricVersionDto {
  id: number
  rubricKey: string
  stage: string
  isActive: number
  modes?: string[]
  dimensions: AdminRubricDimension[]
}

export interface AdminAuditLogItem {
  id: number
  adminUserId: number
  adminNickname: string | null
  action: string
  resourceType: string
  resourceId: string | null
  targetUserId: number | null
  beforeJson: string | null
  afterJson: string | null
  ip: string | null
  userAgent: string | null
  createdAt: string
}

let cachedAdminMe: AdminMe | null = null
let pendingAdminMe: Promise<AdminMe> | null = null
let cachedToken: string | null = null

export function clearAdminMeCache() {
  cachedAdminMe = null
  pendingAdminMe = null
  cachedToken = null
}

export async function getAdminMe(force = false): Promise<AdminMe> {
  const token = getToken() ?? null
  if (!token) {
    clearAdminMeCache()
  } else if (cachedToken !== token) {
    // Token changed (switch account / re-login), drop stale admin cache.
    clearAdminMeCache()
  }
  if (!force && cachedAdminMe) return cachedAdminMe
  if (!force && pendingAdminMe) return pendingAdminMe
  pendingAdminMe = http.get<AdminMe>('/admin/auth/me').then((res) => {
    cachedToken = token
    cachedAdminMe = res.data
    return res.data
  }).finally(() => {
    pendingAdminMe = null
  })
  return pendingAdminMe
}

export const adminApi = {
  getAdminMe,
  listUsers(params: Record<string, unknown>) {
    return http.get<AdminPageResponse<AdminUserListItem>>('/admin/users', { params }).then((r) => r.data)
  },
  getUserDetail(userId: number) {
    return http.get<AdminUserDetail>(`/admin/users/${userId}`).then((r) => r.data)
  },
  updateUserStatus(userId: number, payload: { status: 'active' | 'disabled'; reason?: string }) {
    return http.patch(`/admin/users/${userId}/status`, payload)
  },
  updateUserRoles(userId: number, payload: { adminRoles: string[] }) {
    return http.put(`/admin/users/${userId}/roles`, payload)
  },
  listEssays(params: Record<string, unknown>) {
    return http.get<AdminPageResponse<AdminEssayListItem>>('/admin/essays', { params }).then((r) => r.data)
  },
  getEssayDetail(id: number) {
    return http.get<AdminEssayDetail>(`/admin/essays/${id}`).then((r) => r.data)
  },
  getEssayTask(id: number) {
    return http.get(`/admin/essays/${id}/task`).then((r) => r.data)
  },
  listPrompts(params: Record<string, unknown>) {
    return http.get<AdminPageResponse<AdminPromptDto>>('/admin/prompts', { params }).then((r) => r.data)
  },
  getPrompt(id: string | number) {
    return http.get<AdminPromptDto>(`/admin/prompts/${id}`).then((r) => r.data)
  },
  createPrompt(payload: AdminPromptDto) {
    return http.post<AdminPromptDto>('/admin/prompts', payload).then((r) => r.data)
  },
  updatePrompt(id: string | number, payload: AdminPromptDto) {
    return http.put<AdminPromptDto>(`/admin/prompts/${id}`, payload).then((r) => r.data)
  },
  updatePromptActive(id: string | number, isActive: boolean) {
    return http.patch(`/admin/prompts/${id}/active`, { isActive })
  },
  listRubrics(params: Record<string, unknown>) {
    return http.get<AdminPageResponse<Partial<AdminRubricVersionDto>>>('/admin/rubrics', { params }).then((r) => r.data)
  },
  getRubric(id: string | number) {
    return http.get<AdminRubricVersionDto>(`/admin/rubrics/${id}`).then((r) => r.data)
  },
  cloneRubric(id: string | number, rubricKey?: string) {
    return http.post<AdminRubricVersionDto>(`/admin/rubrics/${id}/clone`, { rubricKey }).then((r) => r.data)
  },
  updateRubric(id: string | number, payload: Pick<AdminRubricVersionDto, 'rubricKey' | 'stage' | 'dimensions'>) {
    return http.put<AdminRubricVersionDto>(`/admin/rubrics/${id}`, payload).then((r) => r.data)
  },
  activateRubric(id: string | number, modeScope = 'all') {
    return http.post<AdminRubricVersionDto>(`/admin/rubrics/${id}/activate`, { modeScope }).then((r) => r.data)
  },
  listAuditLogs(params: Record<string, unknown>) {
    return http.get<AdminPageResponse<AdminAuditLogItem>>('/admin/audit-logs', { params }).then((r) => r.data)
  },
}

