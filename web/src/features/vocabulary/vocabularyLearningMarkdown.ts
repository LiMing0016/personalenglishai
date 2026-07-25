import type {
  VocabularyCardBlock,
  VocabularyCardBlocks,
} from '@/api/vocabulary'

export interface VocabularyMarkdownOutlineItem {
  id: string
  title: string
  level: 2
}

function sectionTitle(block: VocabularyCardBlock) {
  return block.title.trim() || '未命名章节'
}

function listSection(title: string, items: string[]) {
  const content = items.filter(Boolean).map((item) => `- ${item}`).join('\n')
  return [`## ${title}`, content].filter(Boolean).join('\n\n')
}

function markdownBlock(block: VocabularyCardBlock): string {
  const title = sectionTitle(block)

  if (block.type === 'exampleList') {
    const examples = block.content.items.flatMap((item, index) => {
      const lines = [`**${index + 1}. ${item.sentence.trim()}**`]
      if (item.translation.trim()) lines.push(`> ${item.translation.trim()}`)
      return lines
    })
    return [`## ${title}`, examples.join('\n\n')].filter(Boolean).join('\n\n')
  }

  if (block.type === 'collocationList') {
    return listSection(title, block.content.items.map((item) => {
      const expression = item.expression.trim()
      const translation = item.translation.trim()
      return translation ? `**${expression}**：${translation}` : `**${expression}**`
    }))
  }

  if (block.type === 'usageBoundary') {
    const sections = [`## ${title}`]
    if (block.content.useWhen.length) {
      sections.push('### 适合使用', block.content.useWhen.map((item) => `- ${item}`).join('\n'))
    }
    if (block.content.avoidWhen.length) {
      sections.push('### 谨慎使用', block.content.avoidWhen.map((item) => `- ${item}`).join('\n'))
    }
    return sections.join('\n\n')
  }

  if (block.type === 'contrastTable') {
    const sections = [`## ${title}`]
    for (const row of block.content.rows) {
      const details = [
        row.focus.trim() ? `- 侧重点：${row.focus.trim()}` : '',
        row.typicalContext.trim() ? `- 典型语境：${row.typicalContext.trim()}` : '',
      ].filter(Boolean)
      sections.push(`### ${row.term.trim() || '未命名词汇'}`, details.join('\n'))
    }
    return sections.join('\n\n')
  }

  if (block.type === 'memoryTip') {
    return listSection(title, block.content.points)
  }

  const content = block.content.trim()
  if (!content) return `## ${title}`
  return /^##\s+/m.test(content) ? content : `## ${title}\n\n${content}`
}

export function vocabularyCardBlocksToMarkdown(cardBlocks: VocabularyCardBlocks | null | undefined) {
  if (!cardBlocks?.blocks.length) return ''
  return [...cardBlocks.blocks]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map(markdownBlock)
    .filter(Boolean)
    .join('\n\n')
    .trim()
}

function plainHeadingTitle(value: string) {
  return value
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/[`*_~]/g, '')
    .trim()
}

export function buildVocabularyMarkdownOutline(markdown: string): VocabularyMarkdownOutlineItem[] {
  const outline: VocabularyMarkdownOutlineItem[] = []
  for (const line of markdown.replace(/\r\n/g, '\n').split('\n')) {
    const match = /^##(?!#)\s+(.+?)\s*$/.exec(line)
    if (!match) continue
    const title = plainHeadingTitle(match[1] ?? '') || '未命名章节'
    outline.push({
      id: `markdown-outline-${outline.length + 1}`,
      title,
      level: 2,
    })
  }
  return outline
}
