function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeAttribute(text: string): string {
  return escapeHtml(text)
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInlineText(text: string): string {
  return escapeHtml(text)
    .replace(/`([^`]+?)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>')
    .replace(/&lt;br\s*\/?&gt;/gi, '<br/>')
}

function readMarkdownImageSource(sourceText: string): string {
  const trimmed = sourceText.trim()
  const titleStart = trimmed.search(/\s+["']/)
  return titleStart >= 0 ? trimmed.slice(0, titleStart).trim() : trimmed
}

function isSafeImageSource(source: string): boolean {
  const trimmed = source.trim()
  if (/^https?:\/\//i.test(trimmed)) return true
  if (/^blob:/i.test(trimmed)) return true
  if (/^\/(?!\/)/.test(trimmed)) return true
  return /^data:image\/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=\s]+$/i.test(trimmed)
}

function renderMarkdownImage(alt: string, source: string): string {
  if (!isSafeImageSource(source)) return renderInlineText(alt || '图片')
  return [
    '<img',
    ' class="markdown-image"',
    ` src="${escapeAttribute(source)}"`,
    ` alt="${escapeAttribute(alt)}"`,
    ' loading="lazy"',
    '>',
  ].join('')
}

function renderInline(text: string): string {
  const imagePattern = /!\[([^\]]*)\]\(([^)\n]+)\)/g
  let html = ''
  let cursor = 0

  for (const match of text.matchAll(imagePattern)) {
    const start = match.index ?? 0
    html += renderInlineText(text.slice(cursor, start))
    html += renderMarkdownImage(match[1] ?? '', readMarkdownImageSource(match[2] ?? ''))
    cursor = start + match[0].length
  }

  html += renderInlineText(text.slice(cursor))
  return html
}

function renderParagraph(lines: string[]): string {
  return `<p>${lines.map(renderInline).join('<br/>')}</p>`
}

function renderList(lines: string[]): string {
  const items = lines
    .map((line) => line.replace(/^\s*[-*]\s+/, '').trim())
    .map((item) => `<li>${renderInline(item)}</li>`)
    .join('')
  return `<ul>${items}</ul>`
}

function renderOrderedList(lines: string[]): string {
  const items = lines
    .map((line) => line.replace(/^\s*\d+[.)]\s+/, '').trim())
    .map((item) => `<li>${renderInline(item)}</li>`)
    .join('')
  return `<ol>${items}</ol>`
}

function renderBlockquote(lines: string[]): string {
  const quoteLines = lines.map((line) => line.replace(/^>\s?/, ''))
  return `<blockquote>${renderParagraph(quoteLines)}</blockquote>`
}

function renderCodeBlock(language: string, code: string): string {
  const label = language.trim() || 'text'
  return [
    '<div class="markdown-code-block">',
    '<div class="markdown-code-header">',
    `<span>${escapeHtml(label)}</span>`,
    '<button type="button" class="markdown-code-copy" data-markdown-code-copy aria-label="复制文本">复制</button>',
    '</div>',
    `<pre><code>${escapeHtml(code)}</code></pre>`,
    '</div>',
  ].join('')
}

async function writeTextToClipboard(text: string): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  if (typeof document === 'undefined') {
    throw new Error('Clipboard API is not available.')
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

function showCopiedState(button: HTMLButtonElement) {
  const previousText = button.textContent || '复制'
  button.textContent = '已复制'
  button.classList.add('markdown-code-copy--copied')
  window.setTimeout(() => {
    if (!button.isConnected) return
    button.textContent = previousText
    button.classList.remove('markdown-code-copy--copied')
  }, 1200)
}

export async function copyMarkdownCodeFromClick(event: MouseEvent): Promise<boolean> {
  if (typeof Element === 'undefined') return false
  const target = event.target instanceof Element ? event.target : null
  const button = target?.closest<HTMLButtonElement>('[data-markdown-code-copy]')
  if (!button) return false

  event.preventDefault()
  event.stopPropagation()

  const code = button.closest('.markdown-code-block')?.querySelector('code')?.textContent ?? ''
  if (!code) return true

  try {
    await writeTextToClipboard(code)
    showCopiedState(button)
  } catch {
    button.textContent = '复制失败'
    window.setTimeout(() => {
      if (!button.isConnected) return
      button.textContent = '复制'
    }, 1200)
  }
  return true
}

function splitTableRow(line: string): string[] {
  let normalized = line.trim()
  if (normalized.startsWith('|')) normalized = normalized.slice(1)
  if (normalized.endsWith('|')) normalized = normalized.slice(0, -1)

  const cells: string[] = []
  let cell = ''
  for (let index = 0; index < normalized.length; index += 1) {
    const character = normalized[index]
    const previous = normalized[index - 1]
    if (character === '|' && previous !== '\\') {
      cells.push(cell.trim().replace(/\\\|/g, '|'))
      cell = ''
      continue
    }
    cell += character
  }
  cells.push(cell.trim().replace(/\\\|/g, '|'))
  return cells
}

function isTableRowLine(line: string): boolean {
  return line.includes('|') && splitTableRow(line).length >= 2
}

function isTableSeparatorLine(line: string): boolean {
  if (!isTableRowLine(line)) return false
  return splitTableRow(line).every((cell) => /^:?-{3,}:?$/.test(cell))
}

function isTableStart(lines: string[], index: number): boolean {
  const header = lines[index]?.trimEnd()
  const separator = lines[index + 1]?.trimEnd()
  return Boolean(
    header &&
      separator &&
      isTableRowLine(header) &&
      isTableSeparatorLine(separator) &&
      splitTableRow(header).length === splitTableRow(separator).length,
  )
}

function normalizeTableCells(cells: string[], columnCount: number): string[] {
  return Array.from({ length: columnCount }, (_, index) => cells[index] ?? '')
}

function renderTable(lines: string[]): string {
  const headers = splitTableRow(lines[0]!)
  const rows = lines.slice(2).map((line) => normalizeTableCells(splitTableRow(line), headers.length))
  const headerHtml = headers.map((cell) => `<th>${renderInline(cell)}</th>`).join('')
  const rowHtml = rows
    .map((row) => `<tr>${row.map((cell) => `<td>${renderInline(cell)}</td>`).join('')}</tr>`)
    .join('')
  return `<div class="markdown-table-scroll"><table><thead><tr>${headerHtml}</tr></thead><tbody>${rowHtml}</tbody></table></div>`
}

export function renderAssistantMarkdown(markdown: string): string {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const blocks: string[] = []
  let paragraph: string[] = []
  let list: string[] = []
  let orderedList: string[] = []
  let quote: string[] = []

  const flushParagraph = () => {
    if (paragraph.length === 0) return
    blocks.push(renderParagraph(paragraph))
    paragraph = []
  }

  const flushList = () => {
    if (list.length === 0) return
    blocks.push(renderList(list))
    list = []
  }

  const flushOrderedList = () => {
    if (orderedList.length === 0) return
    blocks.push(renderOrderedList(orderedList))
    orderedList = []
  }

  const flushQuote = () => {
    if (quote.length === 0) return
    blocks.push(renderBlockquote(quote))
    quote = []
  }

  const flushAll = () => {
    flushParagraph()
    flushList()
    flushOrderedList()
    flushQuote()
  }

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index]!
    const line = rawLine.trimEnd()
    const trimmed = line.trim()

    const codeFence = /^```([A-Za-z0-9_+.-]+)?\s*$/.exec(trimmed)
    if (codeFence) {
      flushAll()
      const codeLines: string[] = []
      index += 1
      while (index < lines.length && !/^```\s*$/.test(lines[index]!.trim())) {
        codeLines.push(lines[index]!)
        index += 1
      }
      blocks.push(renderCodeBlock(codeFence[1] ?? 'text', codeLines.join('\n')))
      continue
    }

    if (!trimmed) {
      flushAll()
      continue
    }

    if (isTableStart(lines, index)) {
      flushAll()
      const tableLines = [line, lines[index + 1]!.trimEnd()]
      index += 2
      while (index < lines.length && isTableRowLine(lines[index]!.trimEnd())) {
        tableLines.push(lines[index]!.trimEnd())
        index += 1
      }
      index -= 1
      blocks.push(renderTable(tableLines))
      continue
    }

    if (/^---+$/.test(trimmed)) {
      flushAll()
      blocks.push('<hr/>')
      continue
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(trimmed)
    if (heading) {
      flushAll()
      const level = heading[1].length
      blocks.push(`<h${level}>${renderInline(heading[2].trim())}</h${level}>`)
      continue
    }

    if (/^>\s?/.test(trimmed)) {
      flushParagraph()
      flushList()
      flushOrderedList()
      quote.push(trimmed)
      continue
    }

    if (/^\s*[-*]\s+/.test(line)) {
      flushParagraph()
      flushOrderedList()
      flushQuote()
      list.push(line)
      continue
    }

    if (/^\s*\d+[.)]\s+/.test(line)) {
      flushParagraph()
      flushList()
      flushQuote()
      orderedList.push(line)
      continue
    }

    flushList()
    flushOrderedList()
    flushQuote()
    paragraph.push(line)
  }

  flushAll()
  return blocks.join('')
}
