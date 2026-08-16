# Personal Center Subscription Header Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前订阅档位移到个人身份头部，并让 AI Token 卡片的摘要数字随每日、本周、累计视图同步变化。

**Architecture:** `PersonalCenterPage.vue` 持有个人中心唯一的订阅状态，并通过 prop/event 与订阅区共享；一个独立的小组件负责档位标签和有效期浮层。订阅时间与 Token 摘要都由纯函数生成，Vue 组件只处理加载、渲染和交互。

**Tech Stack:** Vue 3、TypeScript、Node Test Runner、Vite、原生 CSS

## Global Constraints

- 不新增后端接口、数据表、图表库、状态库或支付能力。
- 档位标签位于昵称和编辑按钮之间。
- Free 有效期优先使用订阅开始时间；为空时使用注册时间，结束为“长期有效”。
- 付费档使用 `currentPeriodStart` 和 `currentPeriodEnd`。
- 订阅状态加载失败时不渲染档位标签，不伪造档位或日期。
- 删除订阅页的大档位卡和 `0 / 10K` 配额摘要。
- Token 卡左侧只保留“AI Token 活动”一行标题。
- 每日摘要为今天、本周摘要为本周周一至今天、累计摘要为过去一年总量。
- 每日热力图、每周方块矩阵、累计自然月柱状图、兑换码和套餐卡片保持现有功能。

---

## File Structure

- `web/src/components/personal-center/subscriptionPresentation.ts`
  - 负责把订阅 DTO 和注册时间转换为稳定的档位标签、有效期和无障碍文案。
- `web/src/components/personal-center/subscriptionPresentation.test.ts`
  - 覆盖 Free、付费档和缺失日期。
- `web/src/components/personal-center/SubscriptionBadge.vue`
  - 只负责昵称旁的小标签与只读有效期浮层。
- `web/src/pages/app/PersonalCenterPage.vue`
  - 持有唯一订阅状态，向头部和订阅区分发，并接收兑换后的状态更新。
- `web/src/components/personal-center/SubscriptionSection.vue`
  - 删除大档位卡，只加载套餐列表，并在兑换后发出最新订阅状态。
- `web/src/components/personal-center/usageActivity.ts`
  - 新增每日、本周、累计摘要的纯函数。
- `web/src/components/personal-center/usageActivity.test.ts`
  - 覆盖三种摘要口径。
- `web/src/components/personal-center/AiUsageActivityPanel.vue`
  - 使用动态摘要并压缩标题区。
- `design-qa.md`
  - 记录桌面、390px、档位浮层和三种摘要的浏览器证据。

### Task 1: Subscription Presentation Model

**Files:**
- Create: `web/src/components/personal-center/subscriptionPresentation.ts`
- Test: `web/src/components/personal-center/subscriptionPresentation.test.ts`

**Interfaces:**
- Consumes: `SubscriptionStatus` from `@/api/user`
- Produces:

```ts
export interface SubscriptionPresentation {
  planName: string
  periodText: string
  ariaLabel: string
}

export function buildSubscriptionPresentation(
  status: SubscriptionStatus | null,
  profileCreatedAt?: string | null,
): SubscriptionPresentation | null
```

- [ ] **Step 1: Write the failing presentation tests**

Create `subscriptionPresentation.test.ts`:

```ts
import assert from 'node:assert/strict'
import test from 'node:test'

import type { SubscriptionStatus } from '@/api/user'
import { buildSubscriptionPresentation } from './subscriptionPresentation.ts'

test('Free 使用注册时间到长期有效', () => {
  assert.deepEqual(
    buildSubscriptionPresentation(
      subscription({ planCode: 'free', planName: 'Free' }),
      '2026-05-15T09:30:00+08:00',
    ),
    {
      planName: 'Free',
      periodText: '2026-05-15 — 长期有效',
      ariaLabel: '当前档位 Free，有效期 2026-05-15 — 长期有效',
    },
  )
})

test('付费档使用后端订阅周期', () => {
  const result = buildSubscriptionPresentation(subscription({
    planCode: 'pro',
    planName: 'Pro',
    currentPeriodStart: '2026-07-20T10:00:00',
    currentPeriodEnd: '2026-08-20T10:00:00',
  }))

  assert.equal(result?.periodText, '2026-07-20 — 2026-08-20')
})

test('缺失开始时间时不伪造日期', () => {
  const result = buildSubscriptionPresentation(subscription({
    planCode: 'pro',
    planName: 'Pro',
    currentPeriodEnd: '2026-08-20T10:00:00',
  }))

  assert.equal(result?.periodText, '生效时间待同步 — 2026-08-20')
})

function subscription(
  overrides: Partial<SubscriptionStatus> = {},
): SubscriptionStatus {
  return {
    planCode: 'free',
    planName: 'Free',
    currentPeriodStart: null,
    currentPeriodEnd: null,
    usageMonth: '2026-07',
    monthlyTokenLimit: 0,
    tokenUsed: 0,
    tokenRemaining: 0,
    overLimit: false,
    ...overrides,
  }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
cd web
node --test src/components/personal-center/subscriptionPresentation.test.ts
```

Expected: FAIL because `subscriptionPresentation.ts` does not exist.

- [ ] **Step 3: Implement the presentation model**

Create `subscriptionPresentation.ts`:

```ts
import type { SubscriptionStatus } from '@/api/user'

export interface SubscriptionPresentation {
  planName: string
  periodText: string
  ariaLabel: string
}

export function buildSubscriptionPresentation(
  status: SubscriptionStatus | null,
  profileCreatedAt?: string | null,
): SubscriptionPresentation | null {
  if (!status) return null

  const start = isoDate(
    status.currentPeriodStart
      ?? (status.planCode === 'free' ? profileCreatedAt : null),
  )
  const end = status.planCode === 'free'
    ? '长期有效'
    : isoDate(status.currentPeriodEnd) ?? '结束时间待同步'
  const periodText = `${start ?? '生效时间待同步'} — ${end}`

  return {
    planName: status.planName,
    periodText,
    ariaLabel: `当前档位 ${status.planName}，有效期 ${periodText}`,
  }
}

function isoDate(value?: string | null): string | null {
  return value?.match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? null
}
```

- [ ] **Step 4: Run the test to verify GREEN**

Run:

```powershell
cd web
node --test src/components/personal-center/subscriptionPresentation.test.ts
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit the model**

```powershell
git add -- web/src/components/personal-center/subscriptionPresentation.ts web/src/components/personal-center/subscriptionPresentation.test.ts
git commit -m "feat(ui): 增加订阅档位展示模型"
```

### Task 2: Shared Subscription State and Header Badge

**Files:**
- Create: `web/src/components/personal-center/SubscriptionBadge.vue`
- Modify: `web/src/pages/app/PersonalCenterPage.vue`
- Modify: `web/src/components/personal-center/SubscriptionSection.vue`

**Interfaces:**
- Consumes:

```ts
buildSubscriptionPresentation(
  status: SubscriptionStatus | null,
  profileCreatedAt?: string | null,
): SubscriptionPresentation | null
```

- Produces:

```ts
// SubscriptionSection.vue
defineProps<{ status: SubscriptionStatus | null }>()
defineEmits<{ statusUpdated: [status: SubscriptionStatus] }>()
```

- [ ] **Step 1: Create the focused badge component**

Create `SubscriptionBadge.vue`:

```vue
<template>
  <span v-if="presentation" class="subscription-badge-wrap">
    <button
      class="subscription-badge-button"
      type="button"
      :aria-label="presentation.ariaLabel"
      :aria-describedby="tooltipId"
    >
      {{ presentation.planName }}
    </button>
    <span :id="tooltipId" class="subscription-popover" role="tooltip">
      <strong>{{ presentation.planName }}</strong>
      <small>有效期</small>
      <span>{{ presentation.periodText }}</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SubscriptionStatus } from '@/api/user'
import { buildSubscriptionPresentation } from './subscriptionPresentation'

const props = defineProps<{
  status: SubscriptionStatus | null
  profileCreatedAt?: string | null
}>()

const tooltipId = 'profile-subscription-period'
const presentation = computed(() => (
  buildSubscriptionPresentation(props.status, props.profileCreatedAt)
))
</script>
```

Add scoped styles with these exact interaction rules:

```css
.subscription-badge-wrap {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.subscription-badge-button {
  min-height: 24px;
  border: 1px solid #b9dfd3;
  border-radius: 999px;
  padding: 0 9px;
  background: #edf8f4;
  color: #087457;
  cursor: help;
  font-size: 11px;
  font-weight: 780;
}

.subscription-popover {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  left: 0;
  display: grid;
  width: max-content;
  max-width: min(280px, calc(100vw - 32px));
  gap: 3px;
  border: 1px solid #d9e6e1;
  border-radius: 12px;
  padding: 11px 13px;
  background: #fff;
  box-shadow: 0 16px 34px rgba(20, 48, 70, 0.14);
  color: #52677d;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-3px);
  transition: opacity 140ms ease, transform 140ms ease;
}

.subscription-popover strong {
  color: #15304a;
  font-size: 13px;
}

.subscription-popover small {
  color: #8a98a8;
  font-size: 10px;
}

.subscription-popover > span {
  font-size: 12px;
  white-space: nowrap;
}

.subscription-badge-wrap:hover .subscription-popover,
.subscription-badge-wrap:focus-within .subscription-popover {
  opacity: 1;
  transform: translateY(0);
}

.subscription-badge-button:focus-visible {
  outline: 3px solid rgba(4, 120, 87, 0.22);
  outline-offset: 3px;
}
```

- [ ] **Step 2: Hoist the subscription state to the page**

In `PersonalCenterPage.vue`:

1. Import `SubscriptionStatus` and `SubscriptionBadge`.
2. Add `const subscriptionStatus = ref<SubscriptionStatus | null>(null)`.
3. Insert the badge after `<h1>` and before the edit button:

```vue
<SubscriptionBadge
  :status="subscriptionStatus"
  :profile-created-at="profile?.createdAt"
/>
```

4. Pass the shared state to the subscription section:

```vue
<SubscriptionSection
  v-else-if="activeSection === 'subscription'"
  :status="subscriptionStatus"
  @status-updated="subscriptionStatus = $event"
/>
```

5. Add:

```ts
async function refreshSubscriptionStatus() {
  if (isPreviewMode.value) {
    subscriptionStatus.value = {
      planCode: 'free',
      planName: 'Free',
      currentPeriodStart: null,
      currentPeriodEnd: null,
      quotaPeriod: 'daily',
      usageMonth: '2026-07',
      dailyTokenLimit: 10_000,
      monthlyTokenLimit: 0,
      tokenLimit: 10_000,
      tokenUsed: 0,
      tokenRemaining: 10_000,
      overLimit: false,
    }
    return
  }

  try {
    const response = await userApi.getMySubscription()
    subscriptionStatus.value = response.data ?? null
  } catch {
    subscriptionStatus.value = null
    showToast('加载会员信息失败', 'error')
  }
}
```

6. Replace `onMounted(refreshProfile)` with:

```ts
onMounted(() => {
  void Promise.all([refreshProfile(), refreshSubscriptionStatus()])
})
```

- [ ] **Step 3: Convert SubscriptionSection to prop/event state**

In `SubscriptionSection.vue`:

```ts
const props = defineProps<{ status: SubscriptionStatus | null }>()
const emit = defineEmits<{ statusUpdated: [status: SubscriptionStatus] }>()
```

Apply these changes:

- Remove the local `status` ref.
- Remove `effectiveLimit`, `periodText`, and `formatDate`.
- Change template references from `status?.` to `props.status?.`.
- Change `loadSubscription()` to load plans only:

```ts
async function loadPlans() {
  const response = await userApi.getSubscriptionPlans()
  plans.value = response.data ?? []
}
```

- Replace the mounted loader:

```ts
onMounted(async () => {
  try {
    await loadPlans()
  } catch {
    showToast('加载套餐信息失败', 'error')
  }
})
```

- After a successful redemption:

```ts
const nextStatus = res.data
if (nextStatus) emit('statusUpdated', nextStatus)
```

- Delete the complete `.status-panel` template block.
- Change the title to `订阅与兑换码`.
- Remove `.status-panel`, `.eyebrow`, `.plan-name`, `.period`, `.quota-summary`
  and the mobile `.status-panel` rules.
- Keep `.redeem-panel`, `.plans-grid`, the input and all redemption behavior.

- [ ] **Step 4: Run focused tests and frontend build**

Run:

```powershell
cd web
node --test src/components/personal-center/subscriptionPresentation.test.ts
npm run build
```

Expected: 3 tests PASS and Vite build exits `0`.

- [ ] **Step 5: Commit shared state and badge**

```powershell
git add -- web/src/components/personal-center/SubscriptionBadge.vue web/src/pages/app/PersonalCenterPage.vue web/src/components/personal-center/SubscriptionSection.vue
git commit -m "feat(ui): 将订阅档位移至个人信息"
```

### Task 3: Dynamic Usage Headline Model

**Files:**
- Modify: `web/src/components/personal-center/usageActivity.ts`
- Test: `web/src/components/personal-center/usageActivity.test.ts`

**Interfaces:**
- Produces:

```ts
export type UsageActivityMode = 'daily' | 'weekly' | 'cumulative'

export interface UsageHeadline {
  total: number
  label: '今日 Token' | '本周 Token' | '累计 Token'
}

export function buildUsageHeadline(
  mode: UsageActivityMode,
  activity: AiUsageActivity,
  today: string,
): UsageHeadline
```

- [ ] **Step 1: Write the failing headline tests**

Import `buildUsageHeadline` and add:

```ts
test('每日摘要只展示今天的 Token', () => {
  const source = activity([
    day('2026-07-20', 30, { assistant: 30 }),
    day('2026-07-26', 20, { writing: 20 }),
  ])

  assert.deepEqual(buildUsageHeadline('daily', source, '2026-07-26'), {
    total: 20,
    label: '今日 Token',
  })
})

test('每周摘要展示当前自然周 Token', () => {
  const source = activity([
    day('2026-07-20', 30, { assistant: 30 }),
    day('2026-07-26', 20, { writing: 20 }),
  ])

  assert.deepEqual(buildUsageHeadline('weekly', source, '2026-07-26'), {
    total: 50,
    label: '本周 Token',
  })
})

test('累计摘要展示完整查询区间 Token', () => {
  const source = activity([
    day('2025-07-27', 10, { vocabulary: 10 }),
    day('2026-07-26', 20, { writing: 20 }),
  ])

  assert.deepEqual(buildUsageHeadline('cumulative', source, '2026-07-26'), {
    total: 30,
    label: '累计 Token',
  })
})
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
cd web
node --test src/components/personal-center/usageActivity.test.ts
```

Expected: FAIL because `buildUsageHeadline` is not exported.

- [ ] **Step 3: Implement the headline model**

Add to `usageActivity.ts`:

```ts
export type UsageActivityMode = 'daily' | 'weekly' | 'cumulative'

export interface UsageHeadline {
  total: number
  label: '今日 Token' | '本周 Token' | '累计 Token'
}

export function buildUsageHeadline(
  mode: UsageActivityMode,
  activity: AiUsageActivity,
  today: string,
): UsageHeadline {
  if (mode === 'daily') {
    return {
      total: nonNegative(
        activity.buckets.find((bucket) => bucket.date === today)?.total,
      ),
      label: '今日 Token',
    }
  }

  if (mode === 'weekly') {
    const currentWeek = buildWeeklyUsage(activity).find(
      (period) => today >= period.start && today <= period.end,
    )
    return {
      total: nonNegative(currentWeek?.total),
      label: '本周 Token',
    }
  }

  return {
    total: nonNegative(activity.total),
    label: '累计 Token',
  }
}
```

- [ ] **Step 4: Run the test to verify GREEN**

Run:

```powershell
cd web
node --test src/components/personal-center/usageActivity.test.ts
```

Expected: all usage activity tests PASS.

- [ ] **Step 5: Commit the headline model**

```powershell
git add -- web/src/components/personal-center/usageActivity.ts web/src/components/personal-center/usageActivity.test.ts
git commit -m "feat(ui): 增加动态 Token 摘要模型"
```

### Task 4: Compact Activity Header and Browser QA

**Files:**
- Modify: `web/src/components/personal-center/AiUsageActivityPanel.vue`
- Modify: `design-qa.md`
- Create: `design-qa-artifacts/subscription-header-desktop.png`
- Create: `design-qa-artifacts/subscription-header-390.png`

**Interfaces:**
- Consumes:

```ts
buildUsageHeadline(
  mode: UsageActivityMode,
  activity: AiUsageActivity,
  today: string,
): UsageHeadline
```

- [ ] **Step 1: Wire the dynamic headline**

Import:

```ts
import {
  buildUsageHeadline,
  type UsageActivityMode,
} from './usageActivity'
```

Replace the local mode type:

```ts
type ViewMode = UsageActivityMode
```

Add:

```ts
const headline = computed(() => (
  activity.value
    ? buildUsageHeadline(mode.value, activity.value, today)
    : {
        total: 0,
        label: mode.value === 'daily'
          ? '今日 Token'
          : mode.value === 'weekly'
            ? '本周 Token'
            : '累计 Token',
      }
))
```

- [ ] **Step 2: Replace the activity header**

Replace the existing header with:

```vue
<header class="activity-header">
  <h3 id="ai-usage-title">AI Token 活动</h3>
  <div class="activity-total">
    <span>{{ loading ? '—' : formatTokens(headline.total) }}</span>
    <small>{{ headline.label }}</small>
  </div>
</header>
```

Delete `.activity-eyebrow` and `.activity-description` styles. Keep the
existing `activity-header h3`, `.activity-total`, numeric typography and
responsive behavior.

- [ ] **Step 3: Run automated verification**

Run:

```powershell
cd web
node --test src/components/personal-center/subscriptionPresentation.test.ts
node --test src/components/personal-center/usageActivity.test.ts
npm run build
```

Expected: both test files PASS and Vite build exits `0`.

- [ ] **Step 4: Verify in the signed-in in-app browser**

Open:

```text
http://127.0.0.1:4173/app/me?tab=subscription
```

Verify:

1. The current plan badge appears after the nickname and before edit.
2. Hover, click and keyboard focus expose the effective period.
3. The old status panel and `0 / 10K` block are absent.
4. The section heading reads `订阅与兑换码`.
5. Daily shows the current date bucket with `今日 Token`.
6. Weekly shows the Monday-to-today total with `本周 Token`.
7. Cumulative shows `18,298` with `累计 Token` for the current fixture.
8. Redeeming a valid code is not performed during QA; verify the prop/event
   wiring through source inspection and keep the external mutation untouched.
9. Daily, weekly and cumulative charts retain their existing cell/bar counts.
10. At 390px, the badge popover and chart stay inside the document width.
11. Browser console contains no new warnings or errors.

- [ ] **Step 5: Update design QA evidence**

Append to `design-qa.md`:

```markdown
## Subscription Header and Dynamic Usage Summary

- The current plan is a compact badge beside the nickname.
- Hover, click and keyboard focus reveal the real effective period.
- The previous full-width status panel is removed.
- AI Token activity has a single-line title.
- Daily, weekly and cumulative modes show today, current natural week and
  past-year totals respectively.
- Existing charts, redemption and plan cards remain in place.
- Desktop and 390px browser checks pass without document overflow or console errors.
```

Keep `final result: passed` only after the comparison and interaction checks pass.

- [ ] **Step 6: Commit component and QA**

```powershell
git add -- web/src/components/personal-center/AiUsageActivityPanel.vue design-qa.md design-qa-artifacts/subscription-header-desktop.png design-qa-artifacts/subscription-header-390.png
git commit -m "feat(ui): 精简订阅页用量摘要"
```

## Final Verification

- [ ] `node --test src/components/personal-center/subscriptionPresentation.test.ts` from `web`
- [ ] `node --test src/components/personal-center/usageActivity.test.ts` from `web`
- [ ] `npm run build` from `web`
- [ ] `npm run build` from `docs`
- [ ] Signed-in browser desktop and 390px checks pass
- [ ] `git diff --check`
- [ ] `git status --short` is empty

