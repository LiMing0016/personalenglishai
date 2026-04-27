export interface TextOffsetDocChild {
  textContent: string
  nodeSize: number
}

export interface TextOffsetDocLike {
  content: {
    childCount: number
    child(index: number): TextOffsetDocChild
    size: number
  }
}

export function docTextWithParagraphSeparators(doc: TextOffsetDocLike): string {
  const paragraphs: string[] = []

  for (let i = 0; i < doc.content.childCount; i += 1) {
    paragraphs.push(doc.content.child(i).textContent ?? '')
  }

  return paragraphs.join('\n\n')
}

export function textOffsetToDocPos(doc: TextOffsetDocLike, offset: number): number {
  const safeOffset = Math.max(0, offset)
  let charsSeen = 0
  let pos = 0

  for (let i = 0; i < doc.content.childCount; i += 1) {
    const child = doc.content.child(i)
    if (i > 0) charsSeen += 2
    const childText = child.textContent ?? ''
    if (charsSeen + childText.length >= safeOffset) {
      return pos + 1 + (safeOffset - charsSeen)
    }
    charsSeen += childText.length
    pos += child.nodeSize
  }

  return doc.content.size
}
