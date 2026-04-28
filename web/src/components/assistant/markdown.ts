function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function renderInline(text: string): string {
  return escapeHtml(text)
    .replace(/`([^`]+?)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>')
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

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()
    const trimmed = line.trim()

    if (!trimmed) {
      flushAll()
      continue
    }

    if (/^---+$/.test(trimmed)) {
      flushAll()
      blocks.push('<hr/>')
      continue
    }

    const heading = /^(#{1,3})\s+(.+)$/.exec(trimmed)
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
