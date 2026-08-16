# Weekly Token Square Columns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将个人中心 AI Token 活动的“每周”模式从连续高度柱状图替换为参考图中的 53 列 × 7 格方块柱，同时保留真实周总量、产品构成和无障碍交互。

**Architecture:** 继续使用 `/api/users/me/usage` 返回的稀疏日 bucket，并复用 `buildWeeklyUsage` 生成的自然周周期。新增纯函数把每周总量相对范围峰值离散成 0–7 个点亮格；Vue 组件只负责渲染方块列、月份标签与交互详情，“每日”和“累计”分支不改数据口径。

**Tech Stack:** Vue 3、TypeScript、Node Test Runner、Vite、原生 CSS Grid

## Global Constraints

- 只修改“每周”视图；“每日”热力图、“累计”自然月柱状图、当前权益、兑换码和套餐卡片保持不变。
- 每周仍按展示时区的周一至周日聚合，首尾允许部分周，总量必须与日 bucket 守恒。
- 每列固定 7 个方块；空周点亮 0 格，非零周点亮 1–7 格，峰值周点亮 7 格。
- 非零周的点亮格数使用 `ceil(weekTotal / peakWeekTotal * 7)`，并限制在 `1..7`。
- 方块从底部向上连续点亮，空格使用中性浅灰，点亮格沿用个人中心绿色。
- 以整列作为 hover 和键盘 focus 目标，继续展示周范围、Token 总量和产品构成。
- 不新增图表库、状态库、接口或持久化来源。

---

## File Structure

- `web/src/components/personal-center/usageActivity.ts`
  - 保留周聚合职责。
  - 新增周总量到 7 格离散高度的纯函数与返回类型。
- `web/src/components/personal-center/usageActivity.test.ts`
  - 覆盖空周、最小非零周、中间值、峰值和总量守恒。
- `web/src/components/personal-center/AiUsageActivityPanel.vue`
  - 将每周模板拆成独立方块矩阵分支。
  - 复用每日模型的月份列定位。
  - 累计模式继续使用现有连续柱状图。
- `design-qa.md`
  - 记录参考图、每周选中态、桌面和窄屏验证证据。

### Task 1: Weekly Square Mapping Model

**Files:**
- Modify: `web/src/components/personal-center/usageActivity.ts`
- Test: `web/src/components/personal-center/usageActivity.test.ts`

**Interfaces:**
- Consumes: `buildWeeklyUsage(activity: AiUsageActivity): UsagePeriod[]`
- Produces: `buildWeeklySquareColumns(periods: UsagePeriod[]): UsageWeeklySquareColumn[]`
- Produces:

```ts
export interface UsageWeeklySquareColumn {
  period: UsagePeriod
  filledCells: number
}
```

- [ ] **Step 1: Write the failing square-mapping test**

Add the import and test:

```ts
import {
  buildMonthlyUsage,
  buildProductBreakdown,
  buildUsageCalendar,
  buildUsageQueryRange,
  buildWeeklySquareColumns,
  buildWeeklyUsage,
} from './usageActivity.ts'

test('每周总量离散为从 0 到 7 的方块高度', () => {
  const periods = [
    weeklyPeriod('week-empty', 0),
    weeklyPeriod('week-small', 10),
    weeklyPeriod('week-middle', 50),
    weeklyPeriod('week-peak', 100),
  ]

  assert.deepEqual(
    buildWeeklySquareColumns(periods).map((item) => item.filledCells),
    [0, 1, 4, 7],
  )
})
```

Add the test helper:

```ts
function weeklyPeriod(key: string, total: number) {
  return {
    key,
    label: '7/20–7/26',
    start: '2026-07-20',
    end: '2026-07-26',
    total,
    byProduct: {
      assistant: total,
      writing: 0,
      translation: 0,
      vocabulary: 0,
      other: 0,
    },
  }
}
```

- [ ] **Step 2: Run the model test to verify it fails**

Run:

```powershell
cd web
node --test src/components/personal-center/usageActivity.test.ts
```

Expected: FAIL because `buildWeeklySquareColumns` is not exported.

- [ ] **Step 3: Implement the weekly square mapping**

Add the interface and pure function:

```ts
export interface UsageWeeklySquareColumn {
  period: UsagePeriod
  filledCells: number
}

export function buildWeeklySquareColumns(
  periods: UsagePeriod[],
): UsageWeeklySquareColumn[] {
  const peak = Math.max(0, ...periods.map((period) => nonNegative(period.total)))
  return periods.map((period) => {
    const total = nonNegative(period.total)
    const filledCells = total <= 0 || peak <= 0
      ? 0
      : Math.min(7, Math.max(1, Math.ceil((total / peak) * 7)))
    return { period, filledCells }
  })
}
```

- [ ] **Step 4: Run the model tests**

Run:

```powershell
cd web
node --test src/components/personal-center/usageActivity.test.ts
```

Expected: all tests PASS, including `[0, 1, 4, 7]`.

- [ ] **Step 5: Commit the model**

```powershell
git add -- web/src/components/personal-center/usageActivity.ts web/src/components/personal-center/usageActivity.test.ts
git commit -m "feat(ui): 增加每周方块强度模型"
```

### Task 2: Weekly Square Matrix Component

**Files:**
- Modify: `web/src/components/personal-center/AiUsageActivityPanel.vue`
- Modify: `design-qa.md`

**Interfaces:**
- Consumes: `buildWeeklyUsage(activity): UsagePeriod[]`
- Consumes: `buildWeeklySquareColumns(periods): UsageWeeklySquareColumn[]`
- Consumes: `calendar.monthLabels: UsageCalendarMonthLabel[]`
- Preserves: `selectPeriod(period: UsagePeriod)` and `periodAriaLabel(period)`

- [ ] **Step 1: Import and compute weekly square columns**

Extend the import:

```ts
import {
  buildMonthlyUsage,
  buildProductBreakdown,
  buildUsageCalendar,
  buildUsageQueryRange,
  buildWeeklySquareColumns,
  buildWeeklyUsage,
  type UsageCalendarDay,
  type UsagePeriod,
} from './usageActivity'
```

Add:

```ts
const weeklyColumns = computed(() => buildWeeklySquareColumns(weeklyPeriods.value))
```

Change aggregate-period selection so only cumulative mode uses continuous bars:

```ts
const visiblePeriods = computed(() => monthlyPeriods.value)
```

- [ ] **Step 2: Add the dedicated weekly template**

Insert between the daily calendar and cumulative bar branches:

```vue
<div v-else-if="mode === 'weekly'" class="weekly-square-scroll">
  <div class="weekly-square-chart" role="list" aria-label="按周用量趋势">
    <button
      v-for="column in weeklyColumns"
      :key="column.period.key"
      type="button"
      class="weekly-square-column"
      role="listitem"
      :aria-label="periodAriaLabel(column.period)"
      :title="`${column.period.label} · ${formatTokens(column.period.total)} Token`"
      @mouseenter="selectPeriod(column.period)"
      @mouseleave="clearDetail"
      @focus="selectPeriod(column.period)"
      @blur="clearDetail"
    >
      <span
        v-for="cell in 7"
        :key="cell"
        class="weekly-square-cell"
        :class="{ active: cell > 7 - column.filledCells }"
        aria-hidden="true"
      ></span>
    </button>
  </div>
  <div class="weekly-month-labels" aria-hidden="true">
    <span
      v-for="month in calendar.monthLabels"
      :key="`${month.column}-${month.label}`"
      :style="{ gridColumn: `${month.column + 1}` }"
    >
      {{ month.label }}
    </span>
  </div>
</div>

<div v-else class="bars-scroll">
  <div class="bars-chart monthly-bars" role="list" aria-label="按自然月用量趋势">
    <!-- preserve the current monthly usage-bar-wrap loop -->
  </div>
</div>
```

Keep the monthly loop unchanged except that it now renders only `monthlyPeriods`.

- [ ] **Step 3: Replace weekly continuous-bar CSS with square-column CSS**

Remove `.weekly-bars` and add:

```css
.weekly-square-scroll {
  margin-top: 26px;
  overflow-x: auto;
  padding: 8px 2px 2px;
  scrollbar-width: thin;
}

.weekly-square-chart,
.weekly-month-labels {
  display: grid;
  grid-template-columns: repeat(53, 10px);
  gap: 5px;
  min-width: 790px;
}

.weekly-square-chart {
  align-items: stretch;
}

.weekly-square-column {
  display: grid;
  grid-template-rows: repeat(7, 10px);
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.weekly-square-cell {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: #e7eeec;
}

.weekly-square-cell.active {
  background: #168a69;
}

.weekly-square-column:hover .weekly-square-cell.active,
.weekly-square-column:focus-visible .weekly-square-cell.active {
  background: #087457;
}

.weekly-square-column:focus-visible {
  outline: 2px solid #0b8b67;
  outline-offset: 3px;
}

.weekly-month-labels {
  margin-top: 9px;
  min-height: 16px;
  color: #8795a5;
  font-size: 10px;
}
```

- [ ] **Step 4: Run frontend tests and build**

Run:

```powershell
cd web
node --test src/components/personal-center/usageActivity.test.ts
npm run build
```

Expected: model tests PASS and Vite build exits `0`.

- [ ] **Step 5: Verify the selected visual in the in-app browser**

Open the existing signed-in page:

```text
http://127.0.0.1:4173/app/me?tab=subscription
```

Verify:

1. “每周” renders 53 focusable columns and exactly 371 square cells.
2. The zero weeks remain seven neutral cells.
3. Every non-zero week lights at least one cell and the peak week lights all seven.
4. Clicking or focusing a non-zero week shows the same week total and product composition as its aggregated data.
5. “每日” remains a 371-cell calendar.
6. “累计” remains the natural-month continuous bar view.
7. At 390px, horizontal overflow remains inside `.weekly-square-scroll`; the document itself does not overflow.
8. Browser console has no new errors.

- [ ] **Step 6: Update the design QA record**

Append the weekly-square pass to `design-qa.md`:

```markdown
### Weekly Square Columns

- The weekly selected state uses 53 columns with 7 square cells per column.
- Zero weeks remain neutral; non-zero weeks fill 1–7 cells from the bottom.
- The peak week fills all 7 cells and weekly totals still sum to the daily range total.
- Daily and cumulative modes retain their previous visual structures.
- At 390px, the weekly matrix scrolls inside its card without document overflow.
```

Keep `final result: passed` only after the browser comparison passes.

- [ ] **Step 7: Commit the component and QA**

```powershell
git add -- web/src/components/personal-center/AiUsageActivityPanel.vue design-qa.md
git commit -m "feat(ui): 改造每周 Token 方块趋势"
```

## Final Verification

- [ ] `node --test src/components/personal-center/usageActivity.test.ts` from `web`
- [ ] `npm run build` from `web`
- [ ] `npm run build` from `docs`
- [ ] In-app browser desktop and 390px checks pass
- [ ] `git diff --check`
- [ ] `git status --short` is empty
