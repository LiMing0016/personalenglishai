export type SelectionState = { text: string; start: number; end: number }

export function setCursorAt(el: HTMLElement, charOffset: number): boolean {
  const selection = window.getSelection()
  if (!selection) return false

  const range = document.createRange()
  let passed = 0
  const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null)
  let node: Text | null = walker.nextNode() as Text | null

  while (node) {
    const length = node.textContent?.length ?? 0
    if (passed + length >= charOffset) {
      range.setStart(node, charOffset - passed)
      range.collapse(true)
      selection.removeAllRanges()
      selection.addRange(range)
      el.focus()
      return true
    }
    passed += length
    node = walker.nextNode() as Text | null
  }

  if (passed > 0) {
    const last = walker.currentNode as Text
    if (last) {
      range.setStart(last, last.textContent?.length ?? 0)
      range.collapse(true)
      selection.removeAllRanges()
      selection.addRange(range)
      el.focus()
      return true
    }
  }

  return false
}

export function getCurrentCursorOffset(el: HTMLElement | null): number | null {
  if (!el) return null

  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return null

  const range = selection.getRangeAt(0)
  if (!el.contains(range.startContainer)) return null

  try {
    const preRange = document.createRange()
    preRange.selectNodeContents(el)
    preRange.setEnd(range.startContainer, range.startOffset)
    return preRange.toString().length
  } catch {
    return null
  }
}

export function getSelectionState(el: HTMLElement | null, correctionMode: boolean): SelectionState | null {
  if (!el || correctionMode) return null

  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return null

  const range = selection.getRangeAt(0)
  const text = range.toString()
  const trimmed = text.trim()
  if (!trimmed || trimmed.length < 2) return null

  const anchorNode = selection.anchorNode
  const focusNode = selection.focusNode
  const inside =
    anchorNode != null &&
    focusNode != null &&
    el.contains(anchorNode) &&
    el.contains(focusNode)
  if (!inside) return null

  try {
    const startRange = document.createRange()
    startRange.selectNodeContents(el)
    startRange.setEnd(range.startContainer, range.startOffset)
    const start = startRange.toString().length
    const end = start + text.length
    return { text, start, end }
  } catch {
    return { text, start: 0, end: text.length }
  }
}
