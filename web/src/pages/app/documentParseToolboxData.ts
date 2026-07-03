import type {
  TranslationDocumentAssetDto,
  TranslationDocumentParseResponse,
  TranslationParseMode,
  TranslationParseProvider,
} from '../../api/translation'

export interface DocumentParseToolboxRecentItem {
  id: string
  fileName: string
  title: string
  providerLabel: string
  pageLabel: string
  statusLabel: string
  updatedAt: string
}

export type DocumentParseToolboxModelId = 'ppstructure-v3' | 'paddle-vl'

export interface DocumentParseToolboxModelOption {
  id: DocumentParseToolboxModelId
  label: string
  description: string
  parseMode: TranslationParseMode
  provider: TranslationParseProvider
}

export interface DocumentParseToolboxVisualAsset {
  id: string
  pageNumber: number
  label: string
  dataUrl: string
  bbox?: string | null
  confidence?: number | null
}

export interface DocumentParseToolboxAssetPage {
  pageNumber: number
  assets: DocumentParseToolboxVisualAsset[]
}

export const documentParseToolboxModelOptions: DocumentParseToolboxModelOption[] = [
  {
    id: 'ppstructure-v3',
    label: 'PPStructureV3 稳定解析',
    description: '本地结构化模型，适合大多数教材和文本型 PDF。',
    parseMode: 'high_quality',
    provider: 'paddle-ocr',
  },
  {
    id: 'paddle-vl',
    label: 'PaddleOCR-VL 高质量解析',
    description: '本地视觉语言模型，适合扫描、双栏和复杂版面。',
    parseMode: 'high_quality',
    provider: 'local-paddle-vl',
  },
]

export const DEFAULT_DOCUMENT_PARSE_TOOLBOX_MODEL_ID: DocumentParseToolboxModelId = 'paddle-vl'

const RUNNING_JOB_STATUSES = new Set(['PENDING', 'QUEUED', 'RUNNING', 'PROCESSING', 'STARTED'])
const FAILED_JOB_STATUSES = new Set(['FAILED', 'ERROR', 'CANCELLED'])

export function buildDocumentParseToolboxRecentItem(
  parsedDocument: TranslationDocumentParseResponse,
  updatedAt: string,
): DocumentParseToolboxRecentItem {
  return {
    id: parsedDocument.documentId,
    fileName: parsedDocument.fileName,
    title: stripFileExtension(parsedDocument.fileName),
    providerLabel: parsedDocument.provider || parsedDocument.parseJob?.provider || 'PaddleOCR',
    pageLabel: `${Math.max(0, parsedDocument.pageCount || 0)} 页`,
    statusLabel: formatDocumentParseToolboxStatus(parsedDocument),
    updatedAt,
  }
}

export function formatDocumentParseToolboxStatus(parsedDocument: TranslationDocumentParseResponse): string {
  const ocrStatus = normalizeStatus(parsedDocument.ocrStatus)
  const parseStatus = normalizeStatus(parsedDocument.parseStatus)
  const jobStatus = normalizeStatus(parsedDocument.parseJob?.status)

  if (ocrStatus === 'PARTIAL') return '本地 OCR 后台解析中'
  if (FAILED_JOB_STATUSES.has(jobStatus) || parseStatus === 'FAILED' || ocrStatus === 'FAILED') return '解析失败'
  if (RUNNING_JOB_STATUSES.has(jobStatus) || parseStatus === 'PROCESSING') return '正在解析'
  if (parseStatus === 'SUCCEEDED' || ocrStatus === 'SUCCEEDED') return '解析完成'
  return '等待解析'
}

export function shouldRefreshDocumentParseToolboxResult(parsedDocument: TranslationDocumentParseResponse): boolean {
  const ocrStatus = normalizeStatus(parsedDocument.ocrStatus)
  const jobStatus = normalizeStatus(parsedDocument.parseJob?.status)
  const parseStatus = normalizeStatus(parsedDocument.parseStatus)

  if (ocrStatus === 'PARTIAL') return true
  if (FAILED_JOB_STATUSES.has(jobStatus) || parseStatus === 'FAILED' || ocrStatus === 'FAILED') return false
  return RUNNING_JOB_STATUSES.has(jobStatus) || parseStatus === 'PROCESSING'
}

export function buildDocumentParseToolboxAssetPages(
  assets: TranslationDocumentAssetDto[],
): DocumentParseToolboxAssetPage[] {
  const grouped = new Map<number, DocumentParseToolboxVisualAsset[]>()
  for (const asset of assets) {
    if ((asset.assetType ?? '').toLowerCase() !== 'image') continue
    const dataUrl = resolveAssetDataUrl(asset)
    if (!dataUrl) continue
    const pageNumber = Math.max(1, asset.pageNumber || 1)
    const pageAssets = grouped.get(pageNumber) ?? []
    pageAssets.push({
      id: asset.id,
      pageNumber,
      label: labelForAsset(asset),
      dataUrl,
      bbox: asset.bbox,
      confidence: asset.confidence ?? null,
    })
    grouped.set(pageNumber, pageAssets)
  }
  return Array.from(grouped.entries())
    .sort(([left], [right]) => left - right)
    .map(([pageNumber, pageAssets]) => ({
      pageNumber,
      assets: pageAssets,
    }))
}

function normalizeStatus(status: string | null | undefined): string {
  return (status ?? '').trim().toUpperCase()
}

function stripFileExtension(fileName: string): string {
  const trimmed = fileName.trim()
  return trimmed.replace(/\.[^.]+$/, '') || trimmed
}

function resolveAssetDataUrl(asset: TranslationDocumentAssetDto): string {
  const metadata = asset.metadata ?? {}
  const explicitDataUrl = readString(metadata.dataUrl)
  if (explicitDataUrl?.startsWith('data:image/')) return explicitDataUrl

  const dataBase64 = readString(metadata.dataBase64)
  if (!dataBase64) return ''
  const mimeType = readString(metadata.mimeType) || 'image/jpeg'
  if (!mimeType.startsWith('image/')) return ''
  return `data:${mimeType};base64,${dataBase64}`
}

function labelForAsset(asset: TranslationDocumentAssetDto): string {
  const rawType = readString(asset.metadata?.rawType)?.toLowerCase()
  if (rawType === 'chart') return '图表'
  if (rawType === 'header_image') return '页眉图片'
  if (rawType === 'footer_image') return '页脚图片'
  return '图片'
}

function readString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}
