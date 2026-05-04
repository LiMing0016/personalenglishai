export function isAssistantPage(path: string): boolean {
  return path === '/app/assistant' || path.startsWith('/app/assistant/')
}

export function shouldOpenAssistantDrawerForSelection(path: string): boolean {
  return !isAssistantPage(path)
}
