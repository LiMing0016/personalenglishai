import type { WritingCoachEditAction, WritingCoachEditActionType } from '../../types/assistantRequest'

type ToolKey = 'coach' | 'analyze' | 'outline' | 'next' | 'topic' | 'polish' | 'draft' | string

interface ExtractEditActionOptions {
  markdown: string
  selectedText?: string
  selectedSpan?: { start: number; end: number } | null
  selectedToolKey?: ToolKey
}

const EDITABLE_CODE_LANGS = new Set(['', 'text', 'essay-draft', 'plain', 'markdown'])

export function extractWritingCoachEditActions(options: ExtractEditActionOptions): WritingCoachEditAction[] {
  const text = extractFirstEditableCodeBlock(options.markdown)
  if (!text) return []

  const type = inferActionType(options.selectedToolKey, options.markdown, Boolean(options.selectedSpan))
  if (!type) return []

  return [{
    id: `${type}-${hashText(text)}`,
    type,
    title: actionTitle(type),
    text,
    reason: actionReason(type),
    target: buildTarget(type, options.selectedText, options.selectedSpan),
  }]
}

export function extractFirstEditableCodeBlock(markdown: string): string {
  const fencePattern = /```([a-zA-Z0-9_-]*)[^\n\r]*\r?\n([\s\S]*?)```/g
  let match: RegExpExecArray | null
  while ((match = fencePattern.exec(markdown)) !== null) {
    const lang = (match[1] ?? '').trim().toLowerCase()
    const content = (match[2] ?? '').trim()
    if (content && EDITABLE_CODE_LANGS.has(lang)) return content
  }
  return ''
}

function inferActionType(
  selectedToolKey: ToolKey | undefined,
  markdown: string,
  hasSelectedRange: boolean,
): WritingCoachEditActionType | null {
  if (selectedToolKey === 'polish') return hasSelectedRange ? 'replace_selection' : null
  if (selectedToolKey === 'next') return hasSelectedRange ? 'insert_after_selection' : 'append_paragraph'
  if (selectedToolKey === 'draft') return 'append_paragraph'

  if (/替换|改成|润色后|可直接替换/u.test(markdown)) {
    return hasSelectedRange ? 'replace_selection' : null
  }
  if (/后面|插入|补一句|接着写|下一段/u.test(markdown)) {
    return hasSelectedRange ? 'insert_after_selection' : 'append_paragraph'
  }
  if (/追加|新增一段|新段落|正文末尾/u.test(markdown)) return 'append_paragraph'

  return null
}

function buildTarget(
  type: WritingCoachEditActionType,
  selectedText: string | undefined,
  selectedSpan: { start: number; end: number } | null | undefined,
): WritingCoachEditAction['target'] {
  if (type === 'append_paragraph') return { mode: 'document_end' }
  if (selectedSpan) {
    return {
      mode: 'selected_range',
      selectedText,
      range: selectedSpan,
    }
  }
  return {
    mode: 'semantic_match',
    selectedText,
  }
}

function actionTitle(type: WritingCoachEditActionType): string {
  if (type === 'replace_selection') return '替换选中的句子'
  if (type === 'insert_after_selection') return '插入到选中句子后'
  return '追加为新段落'
}

function actionReason(type: WritingCoachEditActionType): string {
  if (type === 'replace_selection') return '适合把当前选中的表达直接改成这一版。'
  if (type === 'insert_after_selection') return '适合承接当前选中的句子继续展开。'
  return '适合放到正文末尾作为新段落。'
}

function hashText(text: string): string {
  let hash = 0
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) >>> 0
  }
  return hash.toString(36)
}
