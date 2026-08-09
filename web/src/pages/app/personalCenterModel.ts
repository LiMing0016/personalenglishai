import type { LocationQuery, LocationQueryRaw } from 'vue-router'

export type PersonalCenterSection =
  | 'overview'
  | 'records'
  | 'assets'
  | 'profile'
  | 'subscription'
  | 'security'

export type AbilityModuleKey =
  | 'writing'
  | 'vocabulary'
  | 'reading'
  | 'listening'
  | 'speaking'

const ABILITY_MODULE_KEYS = new Set<AbilityModuleKey>([
  'writing',
  'vocabulary',
  'reading',
  'listening',
  'speaking',
])

export interface PersonalCenterTab {
  key: PersonalCenterSection
  label: string
}

export const PERSONAL_CENTER_TABS = [
  { key: 'overview', label: '学习概览' },
  { key: 'records', label: '学习记录' },
  { key: 'assets', label: '学习资产' },
  { key: 'profile', label: '能力画像' },
  { key: 'subscription', label: '订阅与用量' },
  { key: 'security', label: '账号安全' },
] as const satisfies readonly PersonalCenterTab[]

const PERSONAL_CENTER_SECTION_KEYS = new Set<PersonalCenterSection>(
  PERSONAL_CENTER_TABS.map((tab) => tab.key),
)

const LEGACY_SECTION_ALIASES: Record<string, PersonalCenterSection> = {
  essays: 'records',
  radar: 'profile',
  settings: 'security',
}

export function parsePersonalCenterSection(
  queryValue: string | string[] | null | undefined,
): PersonalCenterSection {
  const candidate = Array.isArray(queryValue) ? queryValue[0] : queryValue
  if (candidate && LEGACY_SECTION_ALIASES[candidate]) {
    return LEGACY_SECTION_ALIASES[candidate]
  }
  return candidate && PERSONAL_CENTER_SECTION_KEYS.has(candidate as PersonalCenterSection)
    ? candidate as PersonalCenterSection
    : 'overview'
}

export function parseAbilityModule(
  queryValue: string | string[] | null | undefined,
): AbilityModuleKey | null {
  const candidate = Array.isArray(queryValue) ? queryValue[0] : queryValue
  return candidate && ABILITY_MODULE_KEYS.has(candidate as AbilityModuleKey)
    ? candidate as AbilityModuleKey
    : null
}

export function nextPersonalCenterQuery(
  current: LocationQuery,
  section: PersonalCenterSection,
  module: AbilityModuleKey | null = null,
): LocationQueryRaw {
  const next: LocationQueryRaw = { ...current, tab: section }
  delete next.module
  if (section === 'profile' && module) next.module = module
  return next
}
