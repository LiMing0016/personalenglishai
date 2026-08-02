export type AppSkillIcon = 'assistant' | 'writing' | 'translation' | 'reading' | 'listening' | 'speaking'

export interface AppNavItem {
  to: string
  activePrefix: string
  label: string
  skillIcon: AppSkillIcon
}

export const APP_NAV_ITEMS = [
  { to: '/app/assistant', activePrefix: '/app/assistant', label: '学习助手', skillIcon: 'assistant' },
  { to: '/app/writing', activePrefix: '/app/writing', label: '写作', skillIcon: 'writing' },
  { to: '/app/translation', activePrefix: '/app/translation', label: '翻译', skillIcon: 'translation' },
  { to: '/app/vocabulary', activePrefix: '/app/vocabulary', label: '单词', skillIcon: 'reading' },
  { to: '/app/listening', activePrefix: '/app/listening', label: '听力', skillIcon: 'listening' },
  { to: '/app/speaking', activePrefix: '/app/speaking', label: '口语', skillIcon: 'speaking' },
] as const satisfies readonly AppNavItem[]

export function isAppRouteActive(path: string, activePrefix: string) {
  return path === activePrefix || path.startsWith(`${activePrefix}/`)
}
