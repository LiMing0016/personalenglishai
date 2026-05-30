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
  planCode: string
  planName: string | null
  subscriptionStatus: 'active' | 'free' | 'expired' | string
  quotaPeriod: 'daily' | 'monthly' | string
  tokenLimit: number
  tokenUsed: number
  tokenRemaining: number
  overLimit: boolean | number
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  usageMonth: string | null
  usageDate: string | null
}

export interface AdminUserSubscriptionSnapshot {
  planCode: string
  planName: string | null
  subscriptionStatus: 'active' | 'free' | 'expired' | string
  quotaPeriod: 'daily' | 'monthly' | string
  tokenLimit: number
  tokenUsed: number
  tokenRemaining: number
  overLimit: boolean | number
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  usageMonth: string | null
  usageDate: string | null
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
  subscription: AdminUserSubscriptionSnapshot | null
  ability: Record<string, unknown>
  stats: Record<string, unknown>
  recentEvaluations: any[]
  aiUsageRecords?: AdminUserAiUsageRecord[]
  auditLogs?: Array<Record<string, unknown>>
  quickLinks?: AdminUserOverview['quickLinks']
}

export interface AdminUserAiUsageRecord {
  id?: string
  featureKey?: string | null
  provider?: string | null
  model?: string | null
  inputTokens?: number | null
  cachedInputTokens?: number | null
  outputTokens?: number | null
  reasoningTokens?: number | null
  totalTokens?: number | null
  traceId?: string | null
  occurredAt?: string | null
}

export interface AdminUserOverview {
  account: {
    id: number
    nickname: string | null
    email: string | null
    phoneMasked: string | null
    status: string
    studyStage: string | null
    role: string
    adminRoles: string[]
    lastActiveAt: string | null
  }
  subscription: AdminUserSubscriptionSnapshot | null
  writing: {
    recentEvaluations: any[]
    stats?: Record<string, unknown>
  }
  aiUsage: {
    todayTokens: number
    monthTokens: number
    recentFailedRequests: number
  }
  audit: {
    recentLogs: Array<Record<string, unknown>>
  }
  quickLinks: {
    detail: string
    essays: string
    subscriptions: string
    aiUsage: string
    auditLogs: string
  }
}

export interface AdminDataCatalogTable {
  tableName: string
  title: string | null
  module: string | null
  rowCount: number
  sensitivity: 'low' | 'medium' | 'high' | 'critical' | string
  latestAt: string | null
  adminRoute: string | null
  description: string | null
  configured: boolean
}

export interface AdminDataCatalogColumn {
  name: string
  type: string | null
  nullable: boolean
  defaultValue: string | null
  primaryKey: boolean
  sensitive: boolean
  comment: string | null
}

export interface AdminDataCatalogIndex {
  name: string
  columns: string | null
  uniqueIndex: boolean
}

export interface AdminDataCatalogForeignKey {
  name: string
  columnName: string | null
  referencedTableName: string | null
  referencedColumnName: string | null
}

export interface AdminDataCatalogRelationship {
  sourceTable: string
  sourceColumn: string | null
  targetTable: string
  targetColumn: string | null
  relationType: 'physical' | 'logical' | string
  direction: 'incoming' | 'outgoing' | string
  description: string | null
}

export interface AdminDataCatalogTableDetail extends AdminDataCatalogTable {
  columns: AdminDataCatalogColumn[]
  indexes: AdminDataCatalogIndex[]
  foreignKeys: AdminDataCatalogForeignKey[]
  relationships: AdminDataCatalogRelationship[]
  sensitiveColumns: string[]
  securityNotes: string[]
}

export interface AdminDataCatalogGraphNode {
  tableName: string
  title: string | null
  module: string | null
  sensitivity: 'low' | 'medium' | 'high' | 'critical' | string
  rowCount: number
  adminRoute: string | null
  configured: boolean
}

export interface AdminDataCatalogGraphEdge {
  sourceTable: string
  sourceColumn: string | null
  targetTable: string
  targetColumn: string | null
  relationType: 'physical' | 'logical' | string
  description: string | null
}

export interface AdminDataCatalogGraph {
  nodes: AdminDataCatalogGraphNode[]
  edges: AdminDataCatalogGraphEdge[]
}

export interface AdminDataCleaningOverview {
  sourceCount: number
  jobCount: number
  completedJobCount: number
  failedJobCount: number
  runningJobCount: number
}

export interface AdminDataCleaningSource {
  id: number
  sourceUid: string
  sourceType: string
  sourceCode: string
  displayName: string
  licenseStatus: string
  mdxPath: string | null
  mddPath: string | null
  examplesPath: string | null
  coverImagePath: string | null
  metadata: Record<string, unknown>
  status: string
  createdBy: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminDataCleaningJob {
  id: number
  jobUid: string
  sourceUid: string
  jobType: string
  status: string
  progressTotal: number
  progressDone: number
  result: Record<string, unknown>
  errorMessage: string | null
  createdBy: number | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminDictionaryLibrary {
  id: number
  dictionaryUid: string
  sourceUid: string | null
  dictionaryCode: string
  displayName: string
  description: string | null
  format: string
  engineVersion: string | null
  requiredEngineVersion: string | null
  encoding: string | null
  entryCount: number | null
  resourceCount: number | null
  mdxFileName: string | null
  mddFileName: string | null
  coverImagePath: string | null
  mdxSizeBytes: number | null
  mddSizeBytes: number | null
  examplesCount: number | null
  licenseStatus: string
  storageType: string
  enabled: boolean
  sortOrder: number | null
  status: string
  metadata: Record<string, unknown>
  createdBy: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminDictionaryImportJob {
  id: number
  importJobUid: string
  dictionaryUid: string
  sourceUid: string | null
  status: string
  importLimit: number | null
  processedEntries: number
  importedEntries: number
  failedEntries: number
  importedExamples: number
  importedPhrases: number
  errorMessage: string | null
  result: Record<string, unknown>
  createdBy: number | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminDictionaryEntrySample {
  entryUid: string
  headword: string
  partOfSpeech: string | null
  definitionEn?: string | null
  definitionZh?: string | null
  cleanText?: string | null
  qualityScore?: number | null
  exampleCount?: number | null
  phraseCount?: number | null
}

export interface CreateDictionaryDataCleaningSourcePayload {
  sourceCode: string
  displayName: string
  licenseStatus?: string
  mdxPath?: string
  mddPath?: string
  examplesPath?: string
  coverImagePath?: string
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

export interface AdminSubscriptionListItem {
  userId: number
  email: string | null
  phone: string | null
  nickname: string
  userStatus: string
  planCode: string
  planName: string
  subscriptionStatus: 'active' | 'free' | 'expired' | string
  quotaPeriod: 'daily' | 'monthly' | string
  tokenLimit: number
  tokenUsed: number
  tokenRemaining: number
  overLimit: boolean | number
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  usageMonth: string | null
  usageDate: string | null
}

export interface AdminSubscriptionQuotaRule {
  planCode: string
  planName: string
  quotaPeriod: 'daily' | 'monthly' | string
  dailyTokenLimit: number | null
  monthlyTokenLimit: number | null
  active: boolean | number
  sortOrder: number
}

export interface AdminSubscriptionOverview {
  totalUsers: number
  ordinaryUsers: number
  subscribedUsers: number
  todayNewUsers: number
  todayNewSubscriptions: number
  todayFreeTokenUsed: number
  todayPaidTokenUsed: number
  overLimitUsers: number
  sevenDaySubscriptionRate: number
  planDistribution: AdminSubscriptionPlanDistribution[]
  userDiagnostics: AdminSubscriptionUserDiagnostics
  adminUserPreview: AdminSubscriptionAdminUserPreview[]
}

export interface AdminSubscriptionPlanDistribution {
  planCode: string
  planName: string
  userCount: number
  ratio: number
  sortOrder: number
}

export interface AdminSubscriptionUserDiagnostics {
  databaseUserRows: number
  activeUsers: number
  disabledUsers: number
  adminUsers: number
  regularUsers: number
  latestUserCreatedAt: string | null
}

export interface AdminSubscriptionAdminUserPreview {
  userId: number
  email: string | null
  nickname: string | null
  status: string
  studyStage: string | null
  adminRoles: string[]
  lastActiveAt: string | null
}

export interface AdminSubscriptionDailyStat {
  statDate: string
  newUsers: number
  newSubscriptions: number
  ordinaryUsers: number
  subscribedUsers: number
  freeTokenUsed: number
  paidTokenUsed: number
  subscriptionRate: number
}

let cachedAdminMe: AdminMe | null = null
let pendingAdminMe: Promise<AdminMe> | null = null
let cachedToken: string | null = null
const dictionaryUploadTimeoutMs = 600_000

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
  getUserOverview(userId: number) {
    return http.get<AdminUserOverview>(`/admin/users/${userId}/overview`).then((r) => r.data)
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
  listSubscriptions(params: Record<string, unknown>) {
    return http.get<AdminPageResponse<AdminSubscriptionListItem>>('/admin/subscriptions', { params }).then((r) => r.data)
  },
  getSubscriptionOverview() {
    return http.get<AdminSubscriptionOverview>('/admin/subscriptions/overview').then((r) => r.data)
  },
  listSubscriptionDailyStats(params: Record<string, unknown>) {
    return http.get<AdminSubscriptionDailyStat[]>('/admin/subscriptions/daily-stats', { params }).then((r) => r.data)
  },
  listSubscriptionQuotaRules() {
    return http.get<AdminSubscriptionQuotaRule[]>('/admin/subscription/quota-rules').then((r) => r.data)
  },
  updateSubscriptionQuotaRule(planCode: string, payload: { dailyTokenLimit?: number; monthlyTokenLimit?: number }) {
    return http.put<AdminSubscriptionQuotaRule>(`/admin/subscription/quota-rules/${planCode}`, payload).then((r) => r.data)
  },
  listDataCatalogTables(params: Record<string, unknown>) {
    return http.get<AdminDataCatalogTable[]>('/admin/data-catalog/tables', { params }).then((r) => r.data)
  },
  getDataCatalogTable(tableName: string) {
    return http.get<AdminDataCatalogTableDetail>(`/admin/data-catalog/tables/${encodeURIComponent(tableName)}`).then((r) => r.data)
  },
  getDataCatalogGraph(params: Record<string, unknown>) {
    return http.get<AdminDataCatalogGraph>('/admin/data-catalog/graph', { params }).then((r) => r.data)
  },
  exportDataCatalogMermaid(params: Record<string, unknown>) {
    return http.get('/admin/data-catalog/export/mermaid', { params, responseType: 'text' }).then((r) => String(r.data ?? ''))
  },
  exportDataCatalogDbml(params: Record<string, unknown>) {
    return http.get('/admin/data-catalog/export/dbml', { params, responseType: 'text' }).then((r) => String(r.data ?? ''))
  },
  getDataCleaningOverview() {
    return http.get<AdminDataCleaningOverview>('/admin/data-cleaning/overview').then((r) => r.data)
  },
  listDataCleaningSources(params: Record<string, unknown> = {}) {
    return http.get<AdminDataCleaningSource[]>('/admin/data-cleaning/sources', { params }).then((r) => r.data)
  },
  createDictionaryDataCleaningSource(payload: CreateDictionaryDataCleaningSourcePayload) {
    return http.post<AdminDataCleaningSource>('/admin/data-cleaning/dictionary-sources', payload).then((r) => r.data)
  },
  uploadDictionaryDataCleaningSource(payload: CreateDictionaryDataCleaningSourcePayload, files: File[]) {
    const formData = new FormData()
    formData.append('sourceCode', payload.sourceCode)
    formData.append('displayName', payload.displayName)
    formData.append('licenseStatus', payload.licenseStatus || 'unknown')
    for (const file of files) {
      formData.append('files', file)
    }
    return http.post<AdminDataCleaningJob>('/admin/data-cleaning/dictionary-uploads', formData, { timeout: dictionaryUploadTimeoutMs }).then((r) => r.data)
  },
  listDataCleaningJobs(params: Record<string, unknown> = {}) {
    return http.get<AdminDataCleaningJob[]>('/admin/data-cleaning/jobs', { params }).then((r) => r.data)
  },
  createDictionaryProbeJob(sourceUid: string) {
    return http.post<AdminDataCleaningJob>('/admin/data-cleaning/dictionary-probe-jobs', { sourceUid }).then((r) => r.data)
  },
  getDataCleaningJob(jobUid: string) {
    return http.get<AdminDataCleaningJob>(`/admin/data-cleaning/jobs/${encodeURIComponent(jobUid)}`).then((r) => r.data)
  },
  listAdminDictionaries() {
    return http.get<AdminDictionaryLibrary[]>('/admin/dictionaries').then((r) => r.data)
  },
  getAdminDictionary(dictionaryUid: string) {
    return http.get<AdminDictionaryLibrary>(`/admin/dictionaries/${encodeURIComponent(dictionaryUid)}`).then((r) => r.data)
  },
  listAdminDictionaryImportJobs(dictionaryUid: string) {
    return http.get<AdminDictionaryImportJob[]>(`/admin/dictionaries/${encodeURIComponent(dictionaryUid)}/import-jobs`).then((r) => r.data)
  },
  createAdminDictionaryImportJob(dictionaryUid: string, limit = 0) {
    return http.post<AdminDictionaryImportJob>(`/admin/dictionaries/${encodeURIComponent(dictionaryUid)}/import-jobs`, null, { params: { limit } }).then((r) => r.data)
  },
  listAdminDictionaryEntrySamples(dictionaryUid: string, limit = 10) {
    return http.get<AdminDictionaryEntrySample[]>(`/admin/dictionaries/${encodeURIComponent(dictionaryUid)}/entries/samples`, { params: { limit } }).then((r) => r.data)
  },
  listAdminDictionaryImportFailures(importJobUid: string) {
    return http.get<Record<string, unknown>[]>(`/admin/dictionaries/import-jobs/${encodeURIComponent(importJobUid)}/failures`).then((r) => r.data)
  },
}

