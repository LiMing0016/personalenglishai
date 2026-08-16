# English Ability Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the personal center radar with a concise, evidence-aware English ability dashboard and a reusable module detail view while preserving the existing writing Dashboard.

**Architecture:** Keep the current writing APIs and database as the single source of truth. Add a pure personal-center ability presentation model, load server state through TanStack Query, and render one overview plus one reusable detail shell selected by the existing `tab=profile&module=` query state. No frontend component calculates CEFR; current writing data is labeled `待校准`, and unsupported modules use honest empty states.

**Tech Stack:** Vue 3, TypeScript, Vue Router, TanStack Query, Axios, Lucide Vue, Node test runner, Vite.

## Global Constraints

- Keep `/app/writing/dashboard` unchanged and usable.
- Reuse `/api/users/me/profile/ability`, `/api/writing/dashboard`, and `/api/writing/stats`; do not create duplicate persistence or scoring logic.
- Do not display `writingDashboardMock.ts` values as user ability data.
- Do not map 0–100 scores to CEFR in the frontend.
- Show the overall CEFR as `待形成` until a calibrated backend result exists.
- Treat writing, vocabulary, reading, listening, and speaking as the only ability modules; the learning assistant is an action/evidence source.
- Do not add a new Pinia store, browser storage key, chart library, or runtime dependency.
- Preserve the existing personal-center visual system and use `lucide-vue-next` icons.
- A failed module request must not blank the whole ability page or fall back to mock data.
- The desktop overview shows five modules in one row; narrow screens use horizontal scrolling, and detail becomes one column below 760px.

---

## File Structure

### New files

- `web/src/components/personal-center/ability/abilityProfileModel.ts` — pure types, module catalog, evidence/confidence presentation, overview builder, and writing-detail builder.
- `web/src/components/personal-center/ability/abilityProfileModel.test.ts` — Node tests for honest evidence states, routing-independent presentation, and partial data.
- `web/src/components/personal-center/ability/abilityProfilePreview.ts` — development-preview fixtures isolated from production API normalization.
- `web/src/components/personal-center/ability/usePersonalAbilityData.ts` — TanStack Query composition for profile, writing dashboard, and writing stats.
- `web/src/components/personal-center/ability/AbilityProfileSection.vue` — overview/detail orchestration and per-module retry boundaries.
- `web/src/components/personal-center/ability/AbilityOverview.vue` — concise CEFR status strip, five modules, one action, and recent evidence.
- `web/src/components/personal-center/ability/AbilityModuleDetail.vue` — reusable module header, subskills, action, and diagnosis/evidence/history tabs.

### Modified files

- `web/src/api/user.ts` — expose the existing backend `confidence` field on `AbilityProfile`.
- `web/src/pages/app/personalCenterModel.ts` — parse and validate the `module` query parameter.
- `web/src/pages/app/personalCenterModel.test.ts` — cover module query parsing and fallback.
- `web/src/pages/app/PersonalCenterPage.vue` — render the new section and preserve query-based overview/detail navigation.
- `docs/superpowers/specs/2026-08-09-english-ability-dashboard-design.md` — record implementation status after verification.

### Removed file

- `web/src/components/personal-center/AbilityRadarSection.vue` — remove the broken ECharts radar after the new section is integrated.

---

### Task 1: Define the stable ability model and module query contract

**Files:**
- Create: `web/src/components/personal-center/ability/abilityProfileModel.ts`
- Create: `web/src/components/personal-center/ability/abilityProfileModel.test.ts`
- Modify: `web/src/pages/app/personalCenterModel.ts`
- Modify: `web/src/pages/app/personalCenterModel.test.ts`

**Interfaces:**
- Produces: `AbilityModuleKey`, `AbilityEvidenceState`, `AbilityModuleSummary`, `AbilityOverviewModel`, `AbilityModuleDetail`, `parseAbilityModule()`, `buildAbilityOverviewModel()`, and `buildWritingAbilityDetail()`.
- Consumes: `AbilityProfile` from `@/api/user` and writing response types from `@/api/writing`.

- [ ] **Step 1: Write failing module-query and overview-model tests**

Add these cases to `personalCenterModel.test.ts`:

```ts
import { parseAbilityModule } from './personalCenterModel.ts'

test('能力模块查询参数只接受固定英语能力', () => {
  assert.equal(parseAbilityModule('writing'), 'writing')
  assert.equal(parseAbilityModule(['vocabulary']), 'vocabulary')
  assert.equal(parseAbilityModule('assistant'), null)
  assert.equal(parseAbilityModule('unknown'), null)
  assert.equal(parseAbilityModule(undefined), null)
})
```

Create `abilityProfileModel.test.ts` with the first presentation cases:

```ts
import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildAbilityOverviewModel,
  type AbilityModuleKey,
} from './abilityProfileModel.ts'

const moduleKeys: AbilityModuleKey[] = [
  'writing',
  'vocabulary',
  'reading',
  'listening',
  'speaking',
]

test('总览固定展示五项英语能力且不包含学习助手', () => {
  const overview = buildAbilityOverviewModel(null)
  assert.deepEqual(overview.modules.map((item) => item.key), moduleKeys)
  assert.equal(overview.overallLevelLabel, '待形成')
  assert.equal(overview.coverageCount, 0)
})

test('写作评测只形成待校准证据，不由前端生成 CEFR', () => {
  const overview = buildAbilityOverviewModel({
    taskScore: 68,
    coherenceScore: 72,
    grammarScore: 61,
    vocabularyScore: 64,
    structureScore: 70,
    varietyScore: 58,
    assessedScore: 66,
    confidence: 0.7,
    sampleCount: 4,
    updatedAt: '2026-08-09T12:00:00+08:00',
  })
  const writing = overview.modules.find((item) => item.key === 'writing')
  assert.equal(overview.coverageCount, 1)
  assert.equal(writing?.levelLabel, '待校准')
  assert.equal(writing?.evidenceState, 'collecting')
  assert.equal(writing?.evidenceCount, 4)
  assert.equal(overview.modules.find((item) => item.key === 'vocabulary')?.levelLabel, '待测')
})
```

- [ ] **Step 2: Run the tests and verify they fail**

Run from `web`:

```powershell
node --test --experimental-strip-types src/pages/app/personalCenterModel.test.ts src/components/personal-center/ability/abilityProfileModel.test.ts
```

Expected: FAIL because `parseAbilityModule` and `abilityProfileModel.ts` do not exist.

- [ ] **Step 3: Add the module parser and pure presentation types**

Add to `personalCenterModel.ts`:

```ts
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

export function parseAbilityModule(
  queryValue: string | string[] | null | undefined,
): AbilityModuleKey | null {
  const candidate = Array.isArray(queryValue) ? queryValue[0] : queryValue
  return candidate && ABILITY_MODULE_KEYS.has(candidate as AbilityModuleKey)
    ? candidate as AbilityModuleKey
    : null
}
```

Create `abilityProfileModel.ts` with these public shapes:

```ts
import type { AbilityProfile } from '@/api/user'
import type {
  WritingDashboardResponse,
  WritingStatsResponse,
} from '@/api/writing'

export type AbilityModuleKey =
  | 'writing'
  | 'vocabulary'
  | 'reading'
  | 'listening'
  | 'speaking'

export type AbilityEvidenceState =
  | 'unmeasured'
  | 'collecting'
  | 'sufficient'
  | 'stale'
  | 'unavailable'

export interface AbilityModuleSummary {
  key: AbilityModuleKey
  title: string
  levelLabel: string
  evidenceState: AbilityEvidenceState
  evidenceLabel: string
  evidenceCount: number
  actionLabel: string
  actionTo: string
}

export interface AbilityOverviewModel {
  overallLevelLabel: '待形成'
  coverageCount: number
  coverageTotal: 5
  confidenceLabel: '暂无' | '较低' | '中等' | '较高'
  confidenceSteps: 0 | 1 | 2 | 3
  modules: AbilityModuleSummary[]
  priorityText: string
  priorityAction: { label: string; to: string }
  recentEvidence: { label: string; detail: string; timeLabel: string } | null
}

export interface AbilitySubskill {
  key: string
  label: string
  value: number | null
  valueLabel: string
  max: 100
  confidenceLabel: string
}

export interface AbilityModuleDetail extends AbilityModuleSummary {
  diagnosis: string
  trendLabel: string
  subskills: AbilitySubskill[]
  findings: Array<{ tone: 'strength' | 'focus'; text: string }>
  evidence: Array<{ id: string; title: string; scoreLabel: string; timeLabel: string }>
  history: Array<{ id: string; label: string; score: number; delta: number }>
  sourceSummary: string
}
```

Implement `buildAbilityOverviewModel(profile)` with these exact rules:

```ts
const hasWritingEvidence = Boolean(
  profile
  && (profile.sampleCount ?? 0) > 0
  && [
    profile.taskScore,
    profile.coherenceScore,
    profile.grammarScore,
    profile.vocabularyScore,
    profile.structureScore,
    profile.varietyScore,
  ].some((value) => value != null),
)

// Writing is "待校准/收集中" when current evidence exists.
// Vocabulary, reading, listening, and speaking are "待测/无证据".
// The overall level is always "待形成" in this phase.
// The priority action points to writing detail when writing evidence exists;
// otherwise it points to /app/writing/mode.
```

Map `confidence` only to display labels; do not use it to grant CEFR:

```ts
function confidencePresentation(value: number | null | undefined) {
  if (value == null) return { label: '暂无' as const, steps: 0 as const }
  if (value >= 0.8) return { label: '较高' as const, steps: 3 as const }
  if (value >= 0.5) return { label: '中等' as const, steps: 2 as const }
  return { label: '较低' as const, steps: 1 as const }
}
```

- [ ] **Step 4: Run the pure tests and verify they pass**

```powershell
node --test --experimental-strip-types src/pages/app/personalCenterModel.test.ts src/components/personal-center/ability/abilityProfileModel.test.ts
```

Expected: PASS for all personal-center and ability-model cases.

- [ ] **Step 5: Commit the stable model contract**

```powershell
git add web/src/pages/app/personalCenterModel.ts web/src/pages/app/personalCenterModel.test.ts web/src/components/personal-center/ability/abilityProfileModel.ts web/src/components/personal-center/ability/abilityProfileModel.test.ts
git commit -m "feat(ui): 建立英语能力模块展示协议"
```

---

### Task 2: Compose real writing data without duplicating state

**Files:**
- Modify: `web/src/api/user.ts`
- Modify: `web/src/components/personal-center/ability/abilityProfileModel.ts`
- Modify: `web/src/components/personal-center/ability/abilityProfileModel.test.ts`
- Create: `web/src/components/personal-center/ability/abilityProfilePreview.ts`
- Create: `web/src/components/personal-center/ability/usePersonalAbilityData.ts`

**Interfaces:**
- Consumes: `userApi.getAbilityProfile()`, `getWritingDashboard({ range: 'all', mode: 'all' })`, and `getWritingStats()`.
- Produces: `usePersonalAbilityData(previewMode, selectedModule)` and a fully normalized `AbilityModuleDetail` for writing.

- [ ] **Step 1: Add failing writing-detail normalization tests**

Append to `abilityProfileModel.test.ts`:

```ts
import { buildWritingAbilityDetail } from './abilityProfileModel.ts'

test('写作详情复用六项真实能力并保留原始 0-100 口径', () => {
  const detail = buildWritingAbilityDetail(
    {
      taskScore: 68,
      coherenceScore: 72,
      grammarScore: 61,
      vocabularyScore: 64,
      structureScore: 70,
      varietyScore: 58,
      assessedScore: 66,
      confidence: 0.7,
      sampleCount: 4,
      updatedAt: '2026-08-09T12:00:00+08:00',
    },
    {
      scope: { range: 'all', mode: 'all', scorePolicy: 'latest', start: '2026-01-01', end: '2026-08-09', granularity: 'month' },
      overview: { summary: { totalEssays: 4, totalSubmissions: 5, averageScore: 66, bestScore: 75 }, trend: [], insight: '结构稳定，继续提升表达。' },
      growth: {
        essayScoreTrend: [{ essayNo: 1, title: 'Campus life', mode: 'free', score: 66, scoredAt: '2026-08-09T12:00:00+08:00', delta: 4, aiSuggestion: '加强衔接' }],
        scoreDistribution: [], scoreBands: [], highScorePercent: 0, scoreScatter: [],
        monthlyGoal: { done: 1, target: 3, remaining: 2 },
        streak: { currentDays: 1, bestDays: 2, activeDays: 2 },
        insight: '结构稳定，继续提升表达。',
      },
    },
    {
      avgContentQuality: 67,
      avgTaskAchievement: 68,
      avgStructureScore: 70,
      avgVocabularyScore: 64,
      avgGrammarScore: 61,
      avgExpressionScore: 58,
      totalGrammarErrors: 8,
      totalSpellingErrors: 2,
      totalVocabularyErrors: 4,
    },
  )

  assert.equal(detail.levelLabel, '待校准')
  assert.deepEqual(detail.subskills.map((item) => item.value), [68, 72, 61, 64, 70, 58])
  assert.equal(detail.evidence[0]?.title, 'Campus life')
  assert.equal(detail.history[0]?.score, 66)
  assert.match(detail.sourceSummary, /4 次写作评测/)
})

test('写作详情允许 Dashboard 或统计接口部分失败', () => {
  const detail = buildWritingAbilityDetail(null, null, null)
  assert.equal(detail.levelLabel, '待测')
  assert.equal(detail.subskills.every((item) => item.value == null), true)
  assert.deepEqual(detail.evidence, [])
  assert.deepEqual(detail.history, [])
})
```

- [ ] **Step 2: Run the model test and verify it fails**

```powershell
node --test --experimental-strip-types src/components/personal-center/ability/abilityProfileModel.test.ts
```

Expected: FAIL because `buildWritingAbilityDetail` is not implemented and `AbilityProfile.confidence` is absent.

- [ ] **Step 3: Expose confidence and implement the writing-detail builder**

Add to `AbilityProfile` in `web/src/api/user.ts`:

```ts
confidence: number | null
```

Implement `buildWritingAbilityDetail(profile, dashboard, stats)` with these mappings:

```ts
const writingDimensions = [
  ['taskScore', '任务完成'],
  ['coherenceScore', '连贯衔接'],
  ['grammarScore', '语法准确'],
  ['vocabularyScore', '词汇丰富'],
  ['structureScore', '篇章结构'],
  ['varietyScore', '表达多样'],
] as const

// Keep each source score on max=100 and clamp only the progress width.
// Build evidence/history from dashboard.growth.essayScoreTrend.
// Use dashboard.overview.insight as the concise diagnosis when present.
// Use aggregate error totals only; never create article/tense/error-category counts.
// Choose the highest scored non-null dimension as a strength and the lowest as focus.
```

The detail builder must use `profile.sampleCount` for the source summary, format dates defensively, and return empty lists instead of throwing when either writing endpoint is unavailable.

- [ ] **Step 4: Create isolated preview fixtures**

Create `abilityProfilePreview.ts` exporting exact typed fixtures:

```ts
export const PREVIEW_ABILITY_PROFILE: AbilityProfile = {
  taskScore: 68,
  coherenceScore: 72,
  grammarScore: 61,
  vocabularyScore: 64,
  structureScore: 70,
  varietyScore: 58,
  assessedScore: 66,
  confidence: 0.7,
  sampleCount: 4,
  updatedAt: '2026-08-09T12:00:00+08:00',
}
```

Also export `PREVIEW_WRITING_DASHBOARD` and `PREVIEW_WRITING_STATS` with two chronological essay-trend entries and the same field shapes used in the test. These fixtures are consumed only when `route.meta.personalCenterPreview === true`; production queries must never use them as fallback.

- [ ] **Step 5: Add TanStack Query composition**

Create `usePersonalAbilityData.ts`:

```ts
import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'

import { userApi } from '@/api/user'
import { getWritingDashboard, getWritingStats } from '@/api/writing'
import type { AbilityModuleKey } from './abilityProfileModel'
import {
  PREVIEW_ABILITY_PROFILE,
  PREVIEW_WRITING_DASHBOARD,
  PREVIEW_WRITING_STATS,
} from './abilityProfilePreview'

export function usePersonalAbilityData(
  previewMode: Ref<boolean>,
  selectedModule: Ref<AbilityModuleKey | null>,
) {
  const profileQuery = useQuery({
    queryKey: ['personal-center', 'ability', 'profile'],
    queryFn: async () => (await userApi.getAbilityProfile()).data ?? null,
    enabled: computed(() => !previewMode.value),
    staleTime: 60_000,
  })

  const writingEnabled = computed(
    () => !previewMode.value && selectedModule.value === 'writing',
  )
  const writingDashboardQuery = useQuery({
    queryKey: ['personal-center', 'ability', 'writing-dashboard', 'all'],
    queryFn: () => getWritingDashboard({ range: 'all', mode: 'all' }),
    enabled: writingEnabled,
    staleTime: 60_000,
  })
  const writingStatsQuery = useQuery({
    queryKey: ['personal-center', 'ability', 'writing-stats'],
    queryFn: getWritingStats,
    enabled: writingEnabled,
    staleTime: 60_000,
  })

  return {
    profile: computed(() => previewMode.value ? PREVIEW_ABILITY_PROFILE : profileQuery.data.value ?? null),
    writingDashboard: computed(() => previewMode.value ? PREVIEW_WRITING_DASHBOARD : writingDashboardQuery.data.value ?? null),
    writingStats: computed(() => previewMode.value ? PREVIEW_WRITING_STATS : writingStatsQuery.data.value ?? null),
    profileQuery,
    writingDashboardQuery,
    writingStatsQuery,
  }
}
```

- [ ] **Step 6: Run tests and type-check through a production build**

```powershell
node --test --experimental-strip-types src/components/personal-center/ability/abilityProfileModel.test.ts
npm run build
```

Expected: model tests PASS and the build completes without TypeScript errors.

- [ ] **Step 7: Commit real-data composition**

```powershell
git add web/src/api/user.ts web/src/components/personal-center/ability/abilityProfileModel.ts web/src/components/personal-center/ability/abilityProfileModel.test.ts web/src/components/personal-center/ability/abilityProfilePreview.ts web/src/components/personal-center/ability/usePersonalAbilityData.ts
git commit -m "feat(ui): 复用写作能力真实数据"
```

---

### Task 3: Build the concise ability overview

**Files:**
- Create: `web/src/components/personal-center/ability/AbilityOverview.vue`
- Create: `web/src/components/personal-center/ability/AbilityProfileSection.vue`

**Interfaces:**
- Consumes: `AbilityOverviewModel`, selected module, and query state from Tasks 1–2.
- Produces: `open-module` events with an `AbilityModuleKey` and a complete overview screen.

- [ ] **Step 1: Create the overview component with only the approved information hierarchy**

Implement `AbilityOverview.vue` with this public interface:

```ts
const props = defineProps<{
  model: AbilityOverviewModel
  loading: boolean
  error: boolean
}>()

const emit = defineEmits<{
  'open-module': [key: AbilityModuleKey]
  retry: []
}>()
```

Its template must contain exactly these major regions:

```vue
<section class="ability-overview" aria-labelledby="ability-overview-title">
  <header class="ability-heading">
    <h2 id="ability-overview-title">英语能力画像</h2>
    <button type="button">评估说明</button>
  </header>

  <div class="ability-summary-strip">
    <span>综合 CEFR <strong>{{ model.overallLevelLabel }}</strong></span>
    <span>覆盖 <strong>{{ model.coverageCount }}</strong> / {{ model.coverageTotal }}</span>
    <span>可信度 <strong>{{ model.confidenceLabel }}</strong></span>
  </div>

  <div class="ability-module-row" aria-label="英语能力模块">
    <button
      v-for="module in model.modules"
      :key="module.key"
      type="button"
      class="ability-module"
      @click="emit('open-module', module.key)"
    >
      <span>{{ module.title }}</span>
      <strong>{{ module.levelLabel }}</strong>
      <small>{{ module.evidenceLabel }}</small>
    </button>
  </div>

  <div class="ability-priority">
    <strong>{{ model.priorityText }}</strong>
    <RouterLink :to="model.priorityAction.to">{{ model.priorityAction.label }}</RouterLink>
  </div>

  <button v-if="model.recentEvidence" class="ability-recent-evidence" type="button" @click="emit('open-module', 'writing')">
    <span>最近证据</span>
    <strong>{{ model.recentEvidence.detail }}</strong>
    <time>{{ model.recentEvidence.timeLabel }}</time>
  </button>
</section>
```

Use a skeleton that preserves the same strip and five-module row while loading. On profile-query failure, keep the five modules visible as unavailable and show one small retry button; do not replace the whole page with an error panel.

- [ ] **Step 2: Create the section orchestrator**

Implement `AbilityProfileSection.vue` with props and events:

```ts
const props = defineProps<{
  selectedModule: AbilityModuleKey | null
  previewMode: boolean
}>()

const emit = defineEmits<{
  'open-module': [key: AbilityModuleKey]
  'close-module': []
}>()
```

Use `toRef(props, 'previewMode')`, `toRef(props, 'selectedModule')`, `usePersonalAbilityData()`, and `buildAbilityOverviewModel()` without copying query data into local refs. Render `AbilityOverview` when `selectedModule === null`; reserve module detail rendering for Task 4.

- [ ] **Step 3: Add scoped responsive styling**

Implement these layout rules:

```css
.ability-overview { max-width: 1180px; }
.ability-summary-strip { display: grid; grid-template-columns: repeat(3, 1fr); }
.ability-module-row { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); }

@media (max-width: 900px) {
  .ability-module-row { display: flex; overflow-x: auto; }
  .ability-module { min-width: 160px; }
}

@media (max-width: 600px) {
  .ability-summary-strip { grid-template-columns: 1fr; }
  .ability-priority { align-items: stretch; flex-direction: column; }
}
```

Use whitespace and dividers before borders and shadows. Keep all text at 13px or larger except compact status labels at 12px.

- [ ] **Step 4: Run the build**

```powershell
npm run build
```

Expected: PASS with no missing component types or accessibility template errors.

- [ ] **Step 5: Commit the overview**

```powershell
git add web/src/components/personal-center/ability/AbilityOverview.vue web/src/components/personal-center/ability/AbilityProfileSection.vue
git commit -m "feat(ui): 新增英语能力总览"
```

---

### Task 4: Build the reusable module detail and writing diagnosis

**Files:**
- Create: `web/src/components/personal-center/ability/AbilityModuleDetail.vue`
- Modify: `web/src/components/personal-center/ability/AbilityProfileSection.vue`
- Modify: `web/src/components/personal-center/ability/abilityProfileModel.ts`
- Modify: `web/src/components/personal-center/ability/abilityProfileModel.test.ts`

**Interfaces:**
- Consumes: `AbilityModuleDetail`, per-query loading/error state, and `selectedModule`.
- Produces: a consistent writing detail plus honest non-writing empty states using the same shell.

- [ ] **Step 1: Add a failing test for unsupported-module detail states**

Add to `abilityProfileModel.test.ts`:

```ts
import { buildUnavailableAbilityDetail } from './abilityProfileModel.ts'

test('未接入模块沿用统一详情结构但不生成能力结论', () => {
  const detail = buildUnavailableAbilityDetail('vocabulary')
  assert.equal(detail.title, '词汇能力')
  assert.equal(detail.levelLabel, '待测')
  assert.equal(detail.evidenceState, 'unmeasured')
  assert.deepEqual(detail.subskills.map((item) => item.label), [
    '识别理解',
    '主动回忆',
    '语境运用',
  ])
  assert.equal(detail.subskills.every((item) => item.value == null), true)
  assert.equal(detail.actionTo, '/app/vocabulary?tab=modes')
})
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
node --test --experimental-strip-types src/components/personal-center/ability/abilityProfileModel.test.ts
```

Expected: FAIL because `buildUnavailableAbilityDetail` does not exist.

- [ ] **Step 3: Implement module-specific empty configurations**

Add `buildUnavailableAbilityDetail(key)` with exact subskills and actions:

```ts
const unavailableModules = {
  vocabulary: {
    title: '词汇能力',
    subskills: ['识别理解', '主动回忆', '语境运用'],
    actionLabel: '进入单词学习',
    actionTo: '/app/vocabulary?tab=modes',
  },
  reading: {
    title: '阅读能力',
    subskills: ['信息定位', '篇章理解', '推断分析'],
    actionLabel: '导入阅读材料',
    actionTo: '/app/translation',
  },
  listening: {
    title: '听力能力',
    subskills: ['语音辨识', '信息理解', '语篇理解'],
    actionLabel: '进入听力学习',
    actionTo: '/app/listening',
  },
  speaking: {
    title: '口语能力',
    subskills: ['发音', '流利度', '表达组织'],
    actionLabel: '进入口语学习',
    actionTo: '/app/speaking',
  },
} as const
```

All unavailable details use `levelLabel: '待测'`, `evidenceLabel: '暂无有效证据'`, empty evidence/history, and a diagnosis that explains the module has not completed a valid assessment. Do not describe the destination as a diagnostic test when the current destination is only a learning page.

- [ ] **Step 4: Implement the detail component**

Create `AbilityModuleDetail.vue` with:

```ts
type DetailTab = 'diagnosis' | 'evidence' | 'history'

const props = defineProps<{
  detail: AbilityModuleDetail
  loading: boolean
  error: boolean
}>()
const emit = defineEmits<{ back: []; retry: [] }>()
const activeTab = ref<DetailTab>('diagnosis')
```

Render the approved hierarchy:

```vue
<button type="button" class="ability-back" @click="emit('back')">返回英语能力画像</button>

<header class="ability-detail-header">
  <div>
    <h2>{{ detail.title }}</h2>
    <strong>{{ detail.levelLabel }}</strong>
    <span>{{ detail.evidenceLabel }}</span>
    <span>{{ detail.trendLabel }}</span>
  </div>
  <p>{{ detail.diagnosis }}</p>
</header>

<div class="ability-detail-main">
  <section aria-labelledby="subskill-title">
    <h3 id="subskill-title">子能力</h3>
    <div v-for="skill in detail.subskills" :key="skill.key" class="ability-subskill-row">
      <span>{{ skill.label }}</span>
      <strong>{{ skill.valueLabel }}</strong>
      <span class="ability-subskill-track" aria-hidden="true">
        <i :style="{ width: `${skill.value == null ? 0 : Math.min(100, Math.max(0, skill.value))}%` }"></i>
      </span>
    </div>
  </section>

  <aside class="ability-next-step">
    <h3>下一步</h3>
    <p>{{ detail.actionLabel }}</p>
    <RouterLink :to="detail.actionTo">{{ detail.actionLabel }}</RouterLink>
  </aside>
</div>
```

The three tabs must use real `button role="tab"`, `aria-selected`, and one `role="tabpanel"`. Diagnosis shows at most two findings; evidence lists the real essay trend entries; history uses a compact textual score/delta list, not ECharts.

- [ ] **Step 5: Wire writing and non-writing details in the orchestrator**

In `AbilityProfileSection.vue`:

```ts
const detail = computed(() => {
  if (props.selectedModule === 'writing') {
    return buildWritingAbilityDetail(
      data.profile.value,
      data.writingDashboard.value,
      data.writingStats.value,
    )
  }
  return props.selectedModule
    ? buildUnavailableAbilityDetail(props.selectedModule)
    : null
})
```

Writing loading/error state is the union of the two writing detail queries, but render available profile subskills even when only dashboard or stats failed. Retry only the failed queries. Non-writing details never issue writing requests.

- [ ] **Step 6: Run tests and build**

```powershell
node --test --experimental-strip-types src/components/personal-center/ability/abilityProfileModel.test.ts
npm run build
```

Expected: PASS. The built detail contains no ECharts import.

- [ ] **Step 7: Commit reusable details**

```powershell
git add web/src/components/personal-center/ability/AbilityModuleDetail.vue web/src/components/personal-center/ability/AbilityProfileSection.vue web/src/components/personal-center/ability/abilityProfileModel.ts web/src/components/personal-center/ability/abilityProfileModel.test.ts
git commit -m "feat(ui): 新增能力详情与写作诊断"
```

---

### Task 5: Integrate query navigation and remove the radar

**Files:**
- Modify: `web/src/pages/app/PersonalCenterPage.vue`
- Delete: `web/src/components/personal-center/AbilityRadarSection.vue`
- Modify: `web/src/pages/app/personalCenterModel.test.ts`

**Interfaces:**
- Consumes: `parseAbilityModule()` and `AbilityProfileSection` events.
- Produces: refresh-safe and back-friendly `tab=profile&module=<key>` navigation.

- [ ] **Step 1: Add a failing navigation-reset test**

Add a pure query helper to the model test before implementing it:

```ts
import { nextPersonalCenterQuery } from './personalCenterModel.ts'

test('离开能力画像时移除 module，进入详情时保留 profile 页签', () => {
  assert.deepEqual(
    nextPersonalCenterQuery({ tab: 'profile', module: 'writing', vc: '1' }, 'records'),
    { tab: 'records', vc: '1' },
  )
  assert.deepEqual(
    nextPersonalCenterQuery({ tab: 'profile', vc: '1' }, 'profile', 'writing'),
    { tab: 'profile', module: 'writing', vc: '1' },
  )
  assert.deepEqual(
    nextPersonalCenterQuery({ tab: 'profile', module: 'writing' }, 'profile', null),
    { tab: 'profile' },
  )
})
```

- [ ] **Step 2: Run the navigation test and verify it fails**

```powershell
node --test --experimental-strip-types src/pages/app/personalCenterModel.test.ts
```

Expected: FAIL because `nextPersonalCenterQuery` is missing.

- [ ] **Step 3: Implement immutable query updates**

Add to `personalCenterModel.ts`:

```ts
export function nextPersonalCenterQuery(
  current: Record<string, unknown>,
  section: PersonalCenterSection,
  module: AbilityModuleKey | null = null,
) {
  const next = { ...current, tab: section }
  delete next.module
  if (section === 'profile' && module) next.module = module
  return next
}
```

This helper must preserve unrelated query keys while removing stale ability-module state.

- [ ] **Step 4: Replace the radar section in PersonalCenterPage**

Replace the current import and render branch:

```vue
<AbilityProfileSection
  v-else-if="activeSection === 'profile'"
  :selected-module="activeAbilityModule"
  :preview-mode="isPreviewMode"
  @open-module="openAbilityModule"
  @close-module="closeAbilityModule"
/>
```

Add computed/query navigation:

```ts
const activeAbilityModule = computed(() => parseAbilityModule(
  route.query.module as string | string[] | null | undefined,
))

function switchSection(key: PersonalCenterSection) {
  activeSection.value = key
  void router.replace({ query: nextPersonalCenterQuery(route.query, key) })
}

function openAbilityModule(key: AbilityModuleKey) {
  void router.push({ query: nextPersonalCenterQuery(route.query, 'profile', key) })
}

function closeAbilityModule() {
  void router.push({ query: nextPersonalCenterQuery(route.query, 'profile', null) })
}
```

Use `push` for overview/detail transitions so browser Back works. Use `replace` for top-level tab switches to retain current personal-center behavior.

- [ ] **Step 5: Delete the obsolete ECharts component**

Delete `AbilityRadarSection.vue` after confirming no imports remain:

```powershell
rg -n "AbilityRadarSection" src
```

Expected before deletion: only `PersonalCenterPage.vue`; expected after integration: no matches.

- [ ] **Step 6: Run navigation/model tests and build**

```powershell
node --test --experimental-strip-types src/pages/app/personalCenterModel.test.ts src/components/personal-center/ability/abilityProfileModel.test.ts
npm run build
```

Expected: all tests PASS and production build completes.

- [ ] **Step 7: Commit the integration**

```powershell
git add web/src/pages/app/PersonalCenterPage.vue web/src/pages/app/personalCenterModel.ts web/src/pages/app/personalCenterModel.test.ts web/src/components/personal-center/AbilityRadarSection.vue
git commit -m "refactor(ui): 用能力驾驶舱替换雷达画像"
```

---

### Task 6: Browser acceptance, regression checks, and documentation status

**Files:**
- Modify: `docs/superpowers/specs/2026-08-09-english-ability-dashboard-design.md`

**Interfaces:**
- Consumes: the complete personal-center ability feature.
- Produces: verified desktop/mobile behavior and recorded implementation status.

- [ ] **Step 1: Run the focused automated suite**

Run from `web`:

```powershell
node --test --experimental-strip-types src/pages/app/personalCenterModel.test.ts src/components/personal-center/ability/abilityProfileModel.test.ts
npm run build
```

Expected: all tests PASS and Vite emits the production bundle.

- [ ] **Step 2: Verify the development preview overview**

Open:

```text
http://127.0.0.1:4173/dev/personal-center-preview?tab=profile
```

Verify:

- Header and six personal-center tabs remain unchanged.
- Overview shows `综合 CEFR 待形成`, coverage `1 / 5`, and five ability modules.
- No radar chart, ECharts canvas, long explanatory paragraph, or mock `B1+` appears.
- Writing shows `待校准`; other modules show `待测`.
- One priority action and one recent-evidence row are visible.

- [ ] **Step 3: Verify module navigation and detail states**

Verify these URLs and Back behavior:

```text
/dev/personal-center-preview?tab=profile&module=writing
/dev/personal-center-preview?tab=profile&module=vocabulary
/dev/personal-center-preview?tab=profile&module=reading
```

Writing must show six real-shaped subskills, two concise findings, evidence entries, and history without a radar. Vocabulary must show three empty subskills and a real `/app/vocabulary?tab=modes` action. Browser Back must return to the ability overview.

- [ ] **Step 4: Verify responsive and keyboard behavior**

At 1440×1024, 1024×768, 760×900, and 390×844 verify:

- Five modules stay one row on desktop and become horizontally scrollable below 900px.
- Detail becomes one column below 760px.
- No text or status is clipped.
- Tab buttons, modules, back action, retry, and primary action are keyboard reachable.
- Focus rings are visible and current tab state is announced.

- [ ] **Step 5: Verify the existing writing Dashboard is unchanged**

Open:

```text
/app/writing/dashboard
```

Verify the existing overview, growth, ability, errors, topic, practice, and AI tabs still render and that no personal-center component is imported into `WritingPage.vue`.

- [ ] **Step 6: Record implementation status in the design specification**

Append this exact status below “设计结论”:

```markdown
实施状态（2026-08-09）：第一阶段前端底座已完成。个人中心能力总览与统一详情已接入；写作使用现有真实接口，词汇、阅读、听力和口语保持诚实待测状态；原写作 Dashboard 保留。综合 CEFR 与真实词汇等级仍受后端校准和持久化证据约束。
```

- [ ] **Step 7: Commit verification documentation**

```powershell
git add docs/superpowers/specs/2026-08-09-english-ability-dashboard-design.md
git commit -m "docs(ui): 记录能力画像实施状态"
```

- [ ] **Step 8: Confirm final worktree state and merge readiness**

```powershell
git status --short
git log --oneline -6
```

Expected: clean worktree. The feature is eligible for merge review only after automated checks and browser acceptance pass; do not merge automatically.
