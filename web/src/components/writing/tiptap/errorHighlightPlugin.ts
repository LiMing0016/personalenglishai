/**
 * TipTap/ProseMirror plugin: 错误高亮 Decoration
 *
 * 将 ErrorSpan[] 转换为 ProseMirror DecorationSet，在编辑器中渲染
 * 彩色下划线，不修改文档内容。
 */
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import type { EditorView } from '@tiptap/pm/view'
import type { ErrorSpan } from '../buildHighlightedHtml'

export const errorHighlightPluginKey = new PluginKey('errorHighlight')

export interface ErrorHighlightState {
  errors: ErrorSpan[]
  activeErrorId: string | null
  highlightRange: { start: number; end: number } | null
}

function errorCssClasses(error: ErrorSpan, isActive: boolean): string {
  const classes = [`err-${error.type}`, `err-${error.severity}`]
  if (isActive) classes.push('err-active')
  return classes.join(' ')
}

function buildDecorations(
  doc: any,
  state: ErrorHighlightState,
): DecorationSet {
  const decorations: Decoration[] = []
  const text = doc.textContent ?? ''
  const { errors, activeErrorId, highlightRange } = state

  // 将纯文本 offset 映射到 ProseMirror position
  // 多段落时段落间用 \n\n 分隔，每个段落节点 nodeSize = textContent.length + 2
  const textToPos = (offset: number): number => {
    let charsSeen = 0
    let pos = 0
    for (let i = 0; i < doc.content.childCount; i++) {
      const child = doc.content.child(i)
      if (i > 0) charsSeen += 2 // \n\n separator between paragraphs
      const childText = child.textContent
      if (charsSeen + childText.length >= offset) {
        return pos + 1 + (offset - charsSeen) // +1 for paragraph open tag
      }
      charsSeen += childText.length
      pos += child.nodeSize
    }
    return doc.content.size
  }

  // 错误下划线
  for (const error of errors) {
    const { start, end } = error.span
    if (start >= end || start < 0 || end > text.length) continue

    const from = textToPos(start)
    const to = textToPos(end)
    if (from >= to) continue

    const isActive = activeErrorId === error.id
    decorations.push(
      Decoration.inline(from, to, {
        class: errorCssClasses(error, isActive),
        'data-error-ids': error.id,
        nodeName: 'mark',
      }),
    )
  }

  // 句子高亮
  if (highlightRange && highlightRange.start < highlightRange.end) {
    const from = textToPos(highlightRange.start)
    const to = textToPos(highlightRange.end)
    if (from < to) {
      decorations.push(
        Decoration.inline(from, to, {
          class: 'sentence-hl',
        }),
      )
    }
  }

  return DecorationSet.create(doc, decorations)
}

export function createErrorHighlightPlugin() {
  return new Plugin({
    key: errorHighlightPluginKey,
    state: {
      init(): ErrorHighlightState {
        return { errors: [], activeErrorId: null, highlightRange: null }
      },
      apply(tr, value): ErrorHighlightState {
        const meta = tr.getMeta(errorHighlightPluginKey)
        if (meta) return meta
        return value
      },
    },
    props: {
      decorations(state) {
        const pluginState = errorHighlightPluginKey.getState(state)
        if (!pluginState) return DecorationSet.empty
        return buildDecorations(state.doc, pluginState)
      },
    },
  })
}

/**
 * 更新错误高亮状态（从外部调用）
 */
export function setErrorHighlightState(
  view: EditorView,
  state: ErrorHighlightState,
) {
  const tr = view.state.tr.setMeta(errorHighlightPluginKey, state)
  view.dispatch(tr)
}
