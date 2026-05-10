export type AssistantMarkdownTheme = 'marktext' | 'milkdown'

export const ASSISTANT_MARKDOWN_THEME_STORAGE_KEY = 'peai:assistant:markdown-theme'
export const DEFAULT_ASSISTANT_MARKDOWN_THEME: AssistantMarkdownTheme = 'marktext'

export function isAssistantMarkdownTheme(value: unknown): value is AssistantMarkdownTheme {
  return value === 'marktext' || value === 'milkdown'
}

function fallbackStorage(): Storage | undefined {
  try {
    return globalThis.localStorage
  } catch {
    return undefined
  }
}

export function readAssistantMarkdownTheme(
  storage: Storage | undefined = fallbackStorage(),
): AssistantMarkdownTheme {
  const stored = storage?.getItem(ASSISTANT_MARKDOWN_THEME_STORAGE_KEY)
  return isAssistantMarkdownTheme(stored) ? stored : DEFAULT_ASSISTANT_MARKDOWN_THEME
}

export function writeAssistantMarkdownTheme(
  theme: AssistantMarkdownTheme,
  storage: Storage | undefined = fallbackStorage(),
) {
  try {
    storage?.setItem(ASSISTANT_MARKDOWN_THEME_STORAGE_KEY, theme)
  } catch {
    // Theme preference is non-critical; ignore unavailable or full storage.
  }
}
