import type { MarkdownSection } from '../assistant/markdown'

export interface VocabularyCardSection {
  id: string
  title: string
}

export function buildVocabularyCardSections(
  markdownSections: MarkdownSection[],
  hasSources: boolean,
  hasHistory: boolean,
): VocabularyCardSection[] {
  const sections: VocabularyCardSection[] = [
    { id: 'core-information', title: '核心信息' },
    ...markdownSections.map(({ id, title }) => ({ id, title })),
  ]

  if (hasSources) sections.push({ id: 'card-sources', title: '来源' })
  if (hasHistory) sections.push({ id: 'card-history', title: '历史记录' })

  return sections
}
