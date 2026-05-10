import type { AssistantAttachment } from './assistantMock.ts'

export type AssistantAttachmentSource = 'picker' | 'paste' | 'drop'

export interface AssistantRejectedFile {
  file: File
  reason: string
}

export interface AssistantFileValidationResult {
  accepted: File[]
  rejected: AssistantRejectedFile[]
}

const MAX_ATTACHMENT_COUNT = 5
const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

const ALLOWED_IMAGE_TYPES = new Set([
  'image/png',
  'image/jpeg',
  'image/webp',
])

const ALLOWED_PICKER_TYPES = new Set([
  ...ALLOWED_IMAGE_TYPES,
  'application/pdf',
  'text/plain',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
])

const ALLOWED_PICKER_EXTENSIONS = new Set(['.pdf', '.txt', '.doc', '.docx'])

export const assistantAttachmentAccept = [
  'image/png',
  'image/jpeg',
  'image/webp',
  'application/pdf',
  'text/plain',
  '.doc',
  '.docx',
].join(',')

export function validateAssistantFiles(
  files: File[],
  existingAttachments: AssistantAttachment[],
  source: AssistantAttachmentSource,
): AssistantFileValidationResult {
  const accepted: File[] = []
  const rejected: AssistantRejectedFile[] = []

  for (const file of files) {
    if (existingAttachments.length + accepted.length >= MAX_ATTACHMENT_COUNT) {
      rejected.push({ file, reason: `最多只能添加 ${MAX_ATTACHMENT_COUNT} 个项目` })
      continue
    }

    if (file.size > MAX_ATTACHMENT_BYTES) {
      rejected.push({ file, reason: '单个文件最大支持 10MB' })
      continue
    }

    if (!isAllowedFile(file, source)) {
      rejected.push({
        file,
        reason: source === 'picker'
          ? '仅支持 PNG、JPG、WebP、PDF、TXT、DOC、DOCX'
          : '粘贴或拖拽只支持 PNG、JPG、WebP 图片',
      })
      continue
    }

    accepted.push(file)
  }

  return { accepted, rejected }
}

function isAllowedFile(file: File, source: AssistantAttachmentSource) {
  if (source === 'paste' || source === 'drop') {
    return ALLOWED_IMAGE_TYPES.has(file.type)
  }

  if (ALLOWED_PICKER_TYPES.has(file.type)) {
    return true
  }

  return ALLOWED_PICKER_EXTENSIONS.has(fileExtension(file.name))
}

function fileExtension(name: string) {
  const index = name.lastIndexOf('.')
  return index >= 0 ? name.slice(index).toLowerCase() : ''
}
