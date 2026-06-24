import assert from 'node:assert/strict'

import {
  groupTextHitsIntoVisualLines,
  selectTextFlowHits,
  type PdfTextHit,
} from '../src/utils/pdfGeometrySelection'

function makeLine(text: string, top: number, left = 100): PdfTextHit[] {
  return Array.from(text).map((char, index) => {
    const charLeft = left + index * 10
    return {
      text: char,
      left: charLeft,
      top,
      right: charLeft + 10,
      bottom: top + 14,
      width: 10,
      height: 14,
      centerX: charLeft + 5,
      centerY: top + 7,
    }
  })
}

const pageHits = [
  ...makeLine('ABCDE', 10),
  ...makeLine('FGHIJ', 32),
  ...makeLine('KLMNO', 54),
]

const lines = groupTextHitsIntoVisualLines(pageHits)
assert.equal(lines.length, 3, 'text hits should be grouped by visual lines')
assert.equal(lines[0].map((hit) => hit.text).join(''), 'ABCDE')
assert.equal(lines[1].map((hit) => hit.text).join(''), 'FGHIJ')

const forwardSelection = selectTextFlowHits(pageHits, { x: 121, y: 17 }, { x: 126, y: 39 })
assert.equal(
  forwardSelection.map((hit) => hit.text).join(''),
  'BCDEFGH',
  'multi-line drag should select first line tail and last line head instead of applying one rectangle left bound to every line',
)

const reverseSelection = selectTextFlowHits(pageHits, { x: 126, y: 39 }, { x: 121, y: 17 })
assert.equal(
  reverseSelection.map((hit) => hit.text).join(''),
  'BCDEFGH',
  'reverse drag should keep the same text-flow selection result',
)

const sameLineSelection = selectTextFlowHits(pageHits, { x: 112, y: 17 }, { x: 138, y: 17 })
assert.equal(
  sameLineSelection.map((hit) => hit.text).join(''),
  'BCD',
  'same-line drag should still support selecting part of one line',
)

const threeLineSelection = selectTextFlowHits(pageHits, { x: 131, y: 17 }, { x: 112, y: 61 })
assert.equal(
  threeLineSelection.map((hit) => hit.text).join(''),
  'CDEFGHIJKL',
  'middle lines should be selected completely when dragging across more than two lines',
)

console.log('pdf-geometry-selection-ok')
