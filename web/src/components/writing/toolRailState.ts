export function buildToolRailItems(input: { showTaskPrompt: boolean }) {
  const baseItems = [
    { mode: 'score', label: '评价', title: '作文评价' },
    { mode: 'grammarCheck', label: '语法', title: '实时语法检查' },
    { mode: 'rewrite', label: '润色', title: '分级润色' },
    { mode: 'structure', label: '范文', title: '范文' },
    { mode: 'improve', label: '模版', title: '写作模版' },
    { mode: 'explain', label: '素材', title: '写作素材' },
    { mode: 'translate', label: '翻译', title: '翻译' },
    { mode: 'aiNote', label: '教练', title: '写作教练' },
    { mode: 'archive', label: '归档', title: '归档作文' },
  ] as const

  if (!input.showTaskPrompt) return [...baseItems]

  return [
    { mode: 'taskPrompt', label: '题目', title: '当前题目' },
    ...baseItems,
  ] as const
}
