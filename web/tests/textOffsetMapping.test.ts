import test from 'node:test'
import assert from 'node:assert/strict'

import {
  docTextWithParagraphSeparators,
  textOffsetToDocPos,
  type TextOffsetDocLike,
} from '../src/components/writing/tiptap/textOffsetMapping.ts'

function createDoc(paragraphs: string[]): TextOffsetDocLike {
  const children = paragraphs.map((text) => ({
    textContent: text,
    nodeSize: text.length + 2,
  }))

  return {
    content: {
      childCount: children.length,
      child(index: number) {
        return children[index]
      },
      size: children.reduce((total, child) => total + child.nodeSize, 0),
    },
  }
}

test('docTextWithParagraphSeparators preserves logical paragraph gaps', () => {
  const doc = createDoc(['Alpha', 'Beta', 'Gamma'])
  assert.equal(docTextWithParagraphSeparators(doc), 'Alpha\n\nBeta\n\nGamma')
})

test('textOffsetToDocPos maps offsets across paragraphs with two-newline separators', () => {
  const doc = createDoc(['Alpha', 'Beta'])

  assert.equal(textOffsetToDocPos(doc, 0), 1)
  assert.equal(textOffsetToDocPos(doc, 5), 6)
  assert.equal(textOffsetToDocPos(doc, 7), 8)
  assert.equal(textOffsetToDocPos(doc, 11), 12)
})
