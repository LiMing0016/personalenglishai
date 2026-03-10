export interface StageOption {
  value: string
  label: string
  icon: string
}

export const STAGE_OPTIONS: StageOption[] = [
  { value: 'primary', label: '小学', icon: '\uD83C\uDF1F' },
  { value: 'junior', label: '初中', icon: '\uD83D\uDCDA' },
  { value: 'senior', label: '高中', icon: '\uD83C\uDF93' },
  { value: 'cet4', label: '四级', icon: '\uD83D\uDCC4' },
  { value: 'cet6', label: '六级', icon: '\uD83D\uDCC3' },
  { value: 'postgrad', label: '考研', icon: '\uD83C\uDFAF' },
  { value: 'ielts', label: '雅思', icon: '\uD83C\uDF0D' },
  { value: 'toefl', label: '托福', icon: '\uD83C\uDDFA\uD83C\uDDF8' },
]

export const STAGE_LABEL_MAP: Record<string, string> = Object.fromEntries(
  STAGE_OPTIONS.map((s) => [s.value, s.label]),
)

export const STAGE_ID_MAP: Record<string, number> = {
  senior: 1,
  highschool: 1,
  cet4: 2,
  cet6: 3,
  postgrad: 4,
}

export function getStageId(stage: string | null | undefined): number {
  if (!stage) return 2
  return STAGE_ID_MAP[stage] ?? 2
}

export function getStageLabel(stage: string | null | undefined): string {
  if (!stage) return '未设置'
  return STAGE_LABEL_MAP[stage] ?? stage
}
