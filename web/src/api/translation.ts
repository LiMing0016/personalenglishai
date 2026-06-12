import { http } from './http'

export interface TranslationDocumentBlockDto {
  id: string
  type: string
  order: number
  pageNumber: number
  text: string
  confidence: number | null
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
  elapsedMs?: number
  pageCount: number
  blockCount: number
  blocks: TranslationDocumentBlockDto[]
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
