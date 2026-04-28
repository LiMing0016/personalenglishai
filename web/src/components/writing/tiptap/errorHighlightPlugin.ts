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
import { resolveVisibleErrorSpans } from '../errorSpanState'
import { docTextWithParagraphSeparators, textOffsetToDocPos } from './textOffsetMapping'

export const errorHighlightPluginKey = new PluginKey('errorHighlight')

export interface ErrorHighlightState {
  errors: ErrorSpan[]
  activeErrorId: string | null
  highlightRange: { start: number; end: number } | null
}

function errorCssClasses(error: ErrorSpan, isActive: boolean): string {
  const categoryClass = error.category === 'suggestion' ? 'err-category-suggestion' : 'err-category-error'
  const classes = [`err-${error.type}`, `err-${error.severity}`, categoryClass]
  if (isActive) classes.push('err-active')
  return classes.join(' ')
}

function buildDecorations(
  doc: any,
  state: ErrorHighlightState,
): DecorationSet {
  const decorations: Decoration[] = []
  const text = docTextWithParagraphSeparators(doc)
  const { errors, activeErrorId, highlightRange } = state
  const resolvedErrors = resolveVisibleErrorSpans(errors, text)

  // 错误下划线
  for (const error of resolvedErrors) {
    const { start, end } = error.span
    if (start >= end || start < 0 || end > text.length) continue

    const from = textOffsetToDocPos(doc, start)
    const to = textOffsetToDocPos(doc, end)
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
    const from = textOffsetToDocPos(doc, highlightRange.start)
    const to = textOffsetToDocPos(doc, highlightRange.end)
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
