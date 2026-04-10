export function resolveTaskPromptViewerState(input: {
  writingMode?: 'free' | 'exam' | null
  taskPrompt?: string | null
  activePanel?: string | null
}) {
  const visible = input.writingMode === 'exam' || Boolean(input.taskPrompt?.trim())
  const expanded = visible && input.activePanel === 'taskPrompt'

  return {
    visible,
    expanded,
    label: expanded ? '收起题单' : '查看题单',
  }
}
