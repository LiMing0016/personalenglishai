export interface PdfTextPoint {
  x: number
  y: number
}

export interface PdfTextHit {
  text: string
  left: number
  top: number
  right: number
  bottom: number
  width: number
  height: number
  centerX: number
  centerY: number
}

function compareVisualOrder(left: PdfTextHit, right: PdfTextHit) {
  const topDelta = left.top - right.top
  if (Math.abs(topDelta) > Math.max(6, Math.min(left.height, right.height) * 0.6)) return topDelta
  return left.left - right.left
}

function isSameVisualLine(left: PdfTextHit, right: PdfTextHit) {
  return Math.abs(left.centerY - right.centerY) <= Math.max(6, Math.min(left.height, right.height) * 0.7)
}

function lineBoundaryPadding(line: PdfTextHit[]) {
  const averageHeight = line.reduce((sum, hit) => sum + hit.height, 0) / Math.max(1, line.length)
  return Math.max(1, averageHeight * 0.06)
}

function lineCenterY(line: PdfTextHit[]) {
  return line.reduce((sum, hit) => sum + hit.centerY, 0) / Math.max(1, line.length)
}

export function groupTextHitsIntoVisualLines(hits: PdfTextHit[]) {
  const lines: PdfTextHit[][] = []
  for (const hit of [...hits].sort(compareVisualOrder)) {
    const previousLine = lines[lines.length - 1]
    const previousHit = previousLine?.[0]
    if (!previousLine || !previousHit || !isSameVisualLine(previousHit, hit)) {
      lines.push([hit])
    } else {
      previousLine.push(hit)
    }
  }
  return lines.map((line) => line.sort((left, right) => left.left - right.left))
}

export function findClosestTextLineIndex(lines: PdfTextHit[][], point: PdfTextPoint) {
  if (lines.length === 0) return -1

  let closestIndex = 0
  let closestDistance = Number.POSITIVE_INFINITY
  lines.forEach((line, index) => {
    const distance = Math.abs(lineCenterY(line) - point.y)
    if (distance < closestDistance) {
      closestDistance = distance
      closestIndex = index
    }
  })
  return closestIndex
}

export function selectTextFlowHits(hits: PdfTextHit[], start: PdfTextPoint, end: PdfTextPoint) {
  const lines = groupTextHitsIntoVisualLines(hits)
  const startLineIndex = findClosestTextLineIndex(lines, start)
  const endLineIndex = findClosestTextLineIndex(lines, end)
  if (startLineIndex < 0 || endLineIndex < 0) return []

  const isReverseSelection = startLineIndex > endLineIndex
    || (startLineIndex === endLineIndex && start.x > end.x)

  const from = isReverseSelection ? end : start
  const to = isReverseSelection ? start : end
  const firstLineIndex = Math.min(startLineIndex, endLineIndex)
  const lastLineIndex = Math.max(startLineIndex, endLineIndex)
  const selected: PdfTextHit[] = []

  for (let lineIndex = firstLineIndex; lineIndex <= lastLineIndex; lineIndex += 1) {
    const line = lines[lineIndex] ?? []
    const padding = lineBoundaryPadding(line)

    if (firstLineIndex === lastLineIndex) {
      const left = Math.min(from.x, to.x)
      const right = Math.max(from.x, to.x)
      selected.push(...line.filter((hit) => hit.right >= left - padding && hit.left <= right + padding))
      continue
    }

    if (lineIndex === firstLineIndex) {
      selected.push(...line.filter((hit) => hit.right >= from.x - padding))
      continue
    }

    if (lineIndex === lastLineIndex) {
      selected.push(...line.filter((hit) => hit.left <= to.x + padding))
      continue
    }

    selected.push(...line)
  }

  return selected.sort(compareVisualOrder)
}
