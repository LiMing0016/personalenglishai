import { http } from './http'

export interface TranslationDocumentBlockDto {
  id: string
  type: string
  order: number
  pageNumber: number
  text: string
  confidence: number | null
}

export interface TranslationDocumentElementDto extends TranslationDocumentBlockDto {
  bbox?: string | null
  provider?: string | null
  recognitionStatus: string
  qualityScore: number
  metadata: Record<string, unknown>
}

export interface TranslationDocumentOutlineItemDto {
  id: string
  title: string
  level: number
  pageNumber: number
  elementId?: string | null
  bbox?: string | null
  source?: string | null
  confidence?: number | null
}

export interface TranslationDocumentUserBookmarkDto {
  id: string
  title: string
  pageNumber: number
  level: number
  elementId?: string | null
  bbox?: string | null
  source?: string | null
  parentId?: string | null
  order?: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface TranslationDocumentStudyNoteDto {
  id: string
  documentId: string
  bookmarkId?: string | null
  pageNumber: number
  blockId: string
  elementId: string
  bbox?: string | null
  selectedText?: string | null
  title: string
  content: string
  source: 'manual' | 'agent' | string
  status: 'draft' | 'saved' | 'reviewing' | 'mastered' | string
  tags: string[]
  createdAt?: string | null
  updatedAt?: string | null
}

export interface TranslationDocumentWorkspaceStateDto {
  userBookmarks: TranslationDocumentUserBookmarkDto[]
  studyNotes: TranslationDocumentStudyNoteDto[]
  collapsedOutlineItemIds: string[]
  currentPage?: number | null
  activeBlockId?: string | null
  activeOutlineItemId?: string | null
  activeNoteId?: string | null
  updatedAt?: string | null
}

export interface TranslationKnowledgeChunkDto {
  id: string
  chunkOrder: number
  chunkType: string
  content: string
  summary: string
  sourceElementIds: string[]
  pageNumbers: number[]
  tokenCount: number
  qualityScore: number
  embeddingStatus: string
  granularity: string
  startElementOrder: number
  endElementOrder: number
  sectionPath: string[]
  parentChunkId?: string | null
  prevChunkId?: string | null
  nextChunkId?: string | null
}

export interface TranslationSourceCitationDto {
  documentId: string
  chunkId: string
  pageNumber: number | null
  elementId: string | null
  bbox: string | null
  quote: string
  sectionPath: string[]
  score: number
}

export interface TranslationDocumentAgentAnswerRequest {
  question: string
  selectedText?: string | null
  pageNumber?: number | null
  elementId?: string | null
  bbox?: string | null
  mode?: string | null
}

export interface TranslationDocumentAgentAnswerResponse {
  answer: string
  citations: TranslationSourceCitationDto[]
  sourceChunks: TranslationKnowledgeChunkDto[]
}

export interface TranslationDocumentAssetDto {
  id: string
  assetType: string
  pageNumber: number
  sourceElementId?: string | null
  bbox?: string | null
  recognizedText?: string | null
  provider?: string | null
  recognitionStatus: string
  confidence?: number | null
  metadata: Record<string, unknown>
}

export interface TranslationParseDiagnosisDto {
  textLayer: 'GOOD' | 'LOW' | 'NONE' | string
  textCoverageRatio: number
  garbledRatio: number
  headerFooterRatio: number
  imageOnlyPages: number[]
  ocrRecommended: boolean
  highQualityProviderRecommended: boolean
  fallbackRecommended: boolean
  warnings: string[]
}

export interface TranslationDocumentQualityDto {
  documentQualityScore: number
  textCoverageRatio: number
  garbledRatio: number
  locationCoverageRatio: number
  chunkHighQualityRatio: number
  fallbackRecommended: boolean
}

export interface TranslationLanguageProfileDto {
  primaryLanguage: string
  secondaryLanguages: string[]
  languageMixType: string
  languageConfidence: number
}

export interface TranslationParseJobDto {
  jobId: string
  documentId?: string | null
  status: string
  stage: string
  progress: number
  provider?: string | null
  fallbackUsed: boolean
  errorCode?: string | null
}

export interface TranslationDocumentParseResponse {
  documentId: string
  fileName: string
  sourceType: string
  parseStatus: string
  ocrStatus: string
  provider?: string
  parseMode?: 'standard' | 'high_quality'
  fallbackUsed?: boolean
  fileUrl?: string | null
  filePersisted?: boolean
  storageProvider?: string | null
  elapsedMs?: number
  pageCount: number
  blockCount: number
  blocks: TranslationDocumentBlockDto[]
  elements: TranslationDocumentElementDto[]
  outline?: TranslationDocumentOutlineItemDto[]
  knowledgeChunks: TranslationKnowledgeChunkDto[]
  assets: TranslationDocumentAssetDto[]
  diagnosis: TranslationParseDiagnosisDto
  quality: TranslationDocumentQualityDto
  languageProfile: TranslationLanguageProfileDto
  parseJob: TranslationParseJobDto
  workspaceState?: TranslationDocumentWorkspaceStateDto | null
  warnings: string[]
}

export async function importTranslationDocument(
  file: File,
  mode: 'immersive' | 'exam',
  parseMode: 'standard' | 'high_quality' = 'standard',
): Promise<TranslationDocumentParseResponse> {
  const formData = new FormData()
  formData.append('file', file, file.name)
  formData.append('mode', mode)
  formData.append('parseMode', parseMode)

  const response = await http.post<TranslationDocumentParseResponse>(
    '/translation/documents/import',
    formData,
    { timeout: 120000 },
  )
  return response.data
}

export async function parseTranslationPdfDocument(file: File): Promise<TranslationDocumentParseResponse> {
  return importTranslationDocument(file, 'immersive')
}

export async function getTranslationDocumentKnowledge(documentId: string): Promise<TranslationDocumentParseResponse> {
  const response = await http.get<TranslationDocumentParseResponse>(`/translation/documents/${documentId}/knowledge`)
  return response.data
}

export function getTranslationDocumentFileUrl(documentId: string): string {
  return `/api/translation/documents/${documentId}/file`
}

export async function saveTranslationDocumentWorkspaceState(
  documentId: string,
  state: TranslationDocumentWorkspaceStateDto,
): Promise<TranslationDocumentWorkspaceStateDto> {
  const response = await http.put<TranslationDocumentWorkspaceStateDto>(
    `/translation/documents/${documentId}/workspace-state`,
    state,
  )
  return response.data
}

export async function downloadTranslationDocumentWithBookmarks(
  documentId: string,
): Promise<{ blob: Blob; fileName: string }> {
  const response = await http.get<Blob>(
    `/translation/documents/${documentId}/file-with-bookmarks`,
    { responseType: 'blob' },
  )
  const contentDisposition = response.headers['content-disposition']
  return {
    blob: response.data,
    fileName: resolveDownloadFileName(contentDisposition, `${documentId}-bookmarks.pdf`),
  }
}

export async function answerTranslationDocumentQuestion(
  documentId: string,
  request: TranslationDocumentAgentAnswerRequest,
): Promise<TranslationDocumentAgentAnswerResponse> {
  const response = await http.post<TranslationDocumentAgentAnswerResponse>(
    `/translation/documents/${documentId}/agent-answer`,
    request,
  )
  return response.data
}

function resolveDownloadFileName(contentDisposition: string | undefined, fallback: string): string {
  if (!contentDisposition) return fallback
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] ?? fallback
}
