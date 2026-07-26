<template>
  <section class="activity-card" aria-labelledby="ai-usage-title">
    <header class="activity-header">
      <div>
        <p class="activity-eyebrow">过去一年</p>
        <h3 id="ai-usage-title">全站 AI Token 活动</h3>
        <p class="activity-description">汇总学习助手、写作、翻译与词汇中的可计量 AI 使用。</p>
      </div>
      <div class="activity-total">
        <span>{{ loading ? '—' : formatTokens(activity?.total ?? 0) }}</span>
        <small>累计 Token</small>
      </div>
    </header>

    <div class="mode-switch" role="tablist" aria-label="用量统计周期">
      <button
        v-for="option in modeOptions"
        :key="option.key"
        type="button"
        role="tab"
        :aria-selected="mode === option.key"
        :class="{ active: mode === option.key }"
        @click="mode = option.key"
      >
        {{ option.label }}
      </button>
    </div>

    <div v-if="loading" class="activity-skeleton" aria-label="正在加载 AI 用量">
      <span v-for="index in 159" :key="index"></span>
    </div>

    <div v-else-if="error" class="activity-state">
      <strong>暂时无法加载用量活动</strong>
      <span>当前权益和兑换码仍可正常使用。</span>
      <button type="button" @click="loadActivity">重新加载</button>
    </div>

    <template v-else-if="activity">
      <div v-if="mode === 'daily'" class="calendar-shell">
        <div class="weekday-labels" aria-hidden="true">
          <span>一</span>
          <span></span>
          <span>三</span>
          <span></span>
          <span>五</span>
          <span></span>
          <span>日</span>
        </div>
        <div class="calendar-scroll">
          <div class="month-labels" aria-hidden="true">
            <span
              v-for="month in calendar.monthLabels"
              :key="`${month.column}-${month.label}`"
              :style="{ gridColumn: `${month.column + 1}` }"
            >
              {{ month.label }}
            </span>
          </div>
          <div class="calendar-grid">
            <button
              v-for="day in calendar.days"
              :key="day.date"
              type="button"
              class="calendar-day"
              :class="[
                `level-${day.level}`,
                { outside: !day.inRange, today: day.isToday },
              ]"
              :disabled="!day.inRange"
              :aria-label="dayAriaLabel(day)"
              :title="dayTitle(day)"
              @mouseenter="selectDay(day)"
              @mouseleave="clearDetail"
              @focus="selectDay(day)"
              @blur="clearDetail"
            ></button>
          </div>
        </div>
      </div>

      <div v-else class="bars-scroll">
        <div
          class="bars-chart"
          :class="mode === 'weekly' ? 'weekly-bars' : 'monthly-bars'"
          role="list"
          :aria-label="mode === 'weekly' ? '按周用量趋势' : '按自然月用量趋势'"
        >
          <button
            v-for="period in visiblePeriods"
            :key="period.key"
            type="button"
            class="usage-bar-wrap"
            role="listitem"
            :aria-label="periodAriaLabel(period)"
            :title="`${period.label} · ${formatTokens(period.total)} Token`"
            @mouseenter="selectPeriod(period)"
            @mouseleave="clearDetail"
            @focus="selectPeriod(period)"
            @blur="clearDetail"
          >
            <span
              class="usage-bar"
              :class="{ empty: period.total === 0 }"
              :style="{ height: barHeight(period.total) }"
            ></span>
            <small v-if="mode === 'cumulative'">{{ period.label.replace(/^\d{4}年/, '') }}</small>
          </button>
        </div>
      </div>

      <div class="chart-footer">
        <div class="activity-detail" aria-live="polite">
          <template v-if="activeDetail">
            <span>{{ activeDetail.label }}</span>
            <strong>{{ formatTokens(activeDetail.total) }} Token</strong>
            <small>{{ detailComposition(activeDetail.byProduct) }}</small>
          </template>
          <template v-else-if="activity.total > 0">
            <span>悬停或聚焦查看详情</span>
            <strong>{{ activity.timezone }}</strong>
            <small>日期按当前统计时区归组</small>
          </template>
          <template v-else>
            <span>还没有 AI Token 活动</span>
            <strong>从一次学习开始</strong>
            <small>新的可计量使用会从这里沉淀。</small>
          </template>
        </div>

        <div v-if="mode === 'daily'" class="legend" aria-label="用量颜色说明">
          <span>少</span>
          <i v-for="level in [0, 1, 2, 3, 4]" :key="level" :class="`level-${level}`"></i>
          <span>多</span>
        </div>
      </div>

      <div v-if="breakdown.length" class="breakdown">
        <div class="breakdown-heading">
          <span>用量构成</span>
          <small>按产品能力归集</small>
        </div>
        <div class="breakdown-grid">
          <div v-for="item in breakdown" :key="item.key" class="breakdown-item">
            <div>
              <i :class="`product-${item.key}`"></i>
              <span>{{ item.label }}</span>
            </div>
            <strong>{{ item.percent }}%</strong>
            <small>{{ formatTokens(item.total) }}</small>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  userApi,
  type AiUsageActivity,
  type AiUsageProductTotals,
} from '@/api/user'
import {
  buildMonthlyUsage,
  buildProductBreakdown,
  buildUsageCalendar,
  buildUsageQueryRange,
  buildWeeklyUsage,
  type UsageCalendarDay,
  type UsagePeriod,
} from './usageActivity'

type ViewMode = 'daily' | 'weekly' | 'cumulative'

interface ActivityDetail {
  label: string
  total: number
  byProduct: AiUsageProductTotals
}

const modeOptions: Array<{ key: ViewMode; label: string }> = [
  { key: 'daily', label: '每日' },
  { key: 'weekly', label: '每周' },
  { key: 'cumulative', label: '累计' },
]

const mode = ref<ViewMode>('daily')
const activity = ref<AiUsageActivity | null>(null)
const loading = ref(true)
const error = ref(false)
const activeDetail = ref<ActivityDetail | null>(null)
const today = shanghaiToday()

const calendar = computed(() => (
  activity.value
    ? buildUsageCalendar(activity.value, today)
    : { days: [], monthLabels: [] }
))
const weeklyPeriods = computed(() => (
  activity.value ? buildWeeklyUsage(activity.value) : []
))
const monthlyPeriods = computed(() => (
  activity.value ? buildMonthlyUsage(activity.value) : []
))
const visiblePeriods = computed(() => (
  mode.value === 'weekly' ? weeklyPeriods.value : monthlyPeriods.value
))
const maxPeriodTotal = computed(() => Math.max(
  0,
  ...visiblePeriods.value.map((item) => item.total),
))
const breakdown = computed(() => (
  activity.value ? buildProductBreakdown(activity.value) : []
))

async function loadActivity() {
  loading.value = true
  error.value = false
  const { from, to } = buildUsageQueryRange(shanghaiToday())
  try {
    const response = await userApi.getMyAiUsage({
      from,
      to,
      timezone: 'Asia/Shanghai',
    })
    activity.value = response.data ?? emptyActivity(from, to)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

function selectDay(day: UsageCalendarDay) {
  if (!day.inRange) return
  activeDetail.value = {
    label: formatChineseDate(day.date),
    total: day.total,
    byProduct: day.byProduct,
  }
}

function selectPeriod(period: UsagePeriod) {
  activeDetail.value = {
    label: period.label,
    total: period.total,
    byProduct: period.byProduct,
  }
}

function clearDetail() {
  activeDetail.value = null
}

function barHeight(total: number): string {
  if (total <= 0 || maxPeriodTotal.value <= 0) return '4px'
  return `${Math.max(10, Math.round((total / maxPeriodTotal.value) * 116))}px`
}

function dayAriaLabel(day: UsageCalendarDay): string {
  if (!day.inRange) return '统计范围外'
  return `${formatChineseDate(day.date)}，${formatTokens(day.total)} Token`
}

function dayTitle(day: UsageCalendarDay): string {
  return day.inRange
    ? `${formatChineseDate(day.date)} · ${formatTokens(day.total)} Token`
    : ''
}

function periodAriaLabel(period: UsagePeriod): string {
  return `${period.label}，${formatTokens(period.total)} Token`
}

function detailComposition(products: AiUsageProductTotals): string {
  const labels: Array<[keyof AiUsageProductTotals, string]> = [
    ['assistant', '助手'],
    ['writing', '写作'],
    ['translation', '翻译'],
    ['vocabulary', '词汇'],
    ['other', '其他'],
  ]
  const parts = labels
    .filter(([key]) => products[key] > 0)
    .map(([key, label]) => `${label} ${formatTokens(products[key])}`)
  return parts.length ? parts.join(' · ') : '当期没有 Token 消耗'
}

function emptyActivity(from: string, to: string): AiUsageActivity {
  return {
    metric: 'ai_tokens',
    unit: 'token',
    timezone: 'Asia/Shanghai',
    from,
    to,
    total: 0,
    buckets: [],
  }
}

function shanghaiToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

function formatChineseDate(value: string): string {
  return `${Number(value.slice(0, 4))}年${Number(value.slice(5, 7))}月${Number(value.slice(8, 10))}日`
}

function formatTokens(value: number): string {
  return Math.max(0, Number(value) || 0).toLocaleString('zh-CN')
}

onMounted(loadActivity)
</script>

<style scoped>
.activity-card {
  position: relative;
  margin-bottom: 18px;
  padding: 24px;
  overflow: hidden;
  border: 1px solid #dfe8e5;
  border-radius: 18px;
  background:
    radial-gradient(circle at 12% 0%, rgba(16, 185, 129, 0.08), transparent 28%),
    linear-gradient(180deg, #ffffff 0%, #fbfdfc 100%);
  box-shadow: 0 18px 48px rgba(15, 36, 64, 0.055);
  font-variant-numeric: tabular-nums;
}

.activity-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.activity-eyebrow {
  margin: 0 0 6px;
  color: #0b8b67;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.activity-header h3 {
  margin: 0;
  color: #10243f;
  font-size: 21px;
  line-height: 1.25;
}

.activity-description {
  margin: 8px 0 0;
  color: #718298;
  font-size: 13px;
}

.activity-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex: none;
}

.activity-total span {
  color: #10243f;
  font-size: 25px;
  font-weight: 780;
  letter-spacing: -0.035em;
}

.activity-total small {
  margin-top: 2px;
  color: #8493a6;
  font-size: 11px;
}

.mode-switch {
  display: inline-flex;
  gap: 3px;
  margin-top: 22px;
  padding: 3px;
  border: 1px solid #e2e9e7;
  border-radius: 10px;
  background: #f5f8f7;
}

.mode-switch button {
  min-width: 58px;
  padding: 7px 12px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #7a899b;
  font-size: 13px;
  font-weight: 680;
  cursor: pointer;
}

.mode-switch button.active {
  background: #fff;
  color: #0a7f60;
  box-shadow: 0 1px 5px rgba(15, 36, 64, 0.1);
}

.calendar-shell {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 10px;
  margin-top: 22px;
}

.weekday-labels {
  display: grid;
  grid-template-rows: repeat(7, 12px);
  gap: 4px;
  padding-top: 22px;
  color: #9ba8b7;
  font-size: 9px;
  line-height: 12px;
}

.calendar-scroll,
.bars-scroll {
  overflow-x: auto;
  scrollbar-width: thin;
  scrollbar-color: #cfdad7 transparent;
}

.month-labels {
  display: grid;
  grid-template-columns: repeat(53, 12px);
  column-gap: 4px;
  min-width: 844px;
  height: 18px;
  color: #8391a3;
  font-size: 10px;
  white-space: nowrap;
}

.month-labels span {
  align-self: start;
}

.calendar-grid {
  display: grid;
  grid-template-rows: repeat(7, 12px);
  grid-auto-flow: column;
  grid-auto-columns: 12px;
  gap: 4px;
  width: max-content;
}

.calendar-day {
  width: 12px;
  height: 12px;
  padding: 0;
  border: 0;
  border-radius: 3px;
  background: #edf1f0;
  cursor: pointer;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}

.calendar-day:hover:not(:disabled),
.calendar-day:focus-visible:not(:disabled) {
  z-index: 1;
  outline: none;
  transform: scale(1.35);
  box-shadow: 0 0 0 2px #fff, 0 0 0 3px #1c8e70;
}

.calendar-day.outside {
  background: transparent;
  cursor: default;
}

.calendar-day.today {
  box-shadow: inset 0 0 0 1.5px #0f2440;
}

.level-0 { background-color: #edf1f0; }
.level-1 { background-color: #cce9df; }
.level-2 { background-color: #83cbb4; }
.level-3 { background-color: #2c9f7b; }
.level-4 { background-color: #087457; }

.bars-scroll {
  margin-top: 26px;
  padding: 8px 2px 0;
}

.bars-chart {
  display: grid;
  align-items: end;
  gap: 6px;
  min-width: 720px;
  height: 158px;
  padding: 0 4px 22px;
  border-bottom: 1px solid #dfe7e5;
  background:
    linear-gradient(to top, transparent 32%, rgba(219, 229, 226, 0.55) 33%, transparent 34%),
    linear-gradient(to top, transparent 65%, rgba(219, 229, 226, 0.55) 66%, transparent 67%);
}

.weekly-bars {
  grid-template-columns: repeat(52, minmax(7px, 1fr));
}

.monthly-bars {
  grid-template-columns: repeat(12, minmax(44px, 1fr));
  gap: 12px;
}

.usage-bar-wrap {
  position: relative;
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: flex-end;
  min-width: 0;
  height: 136px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.usage-bar-wrap:focus-visible {
  outline: 2px solid #0b8b67;
  outline-offset: 2px;
}

.usage-bar {
  width: 100%;
  min-height: 4px;
  border-radius: 4px 4px 2px 2px;
  background: linear-gradient(180deg, #43b794, #087457);
  transition: filter 0.15s ease, transform 0.15s ease;
}

.usage-bar.empty {
  background: #e7eeec;
}

.usage-bar-wrap:hover .usage-bar,
.usage-bar-wrap:focus-visible .usage-bar {
  filter: saturate(1.15);
  transform: translateY(-2px);
}

.usage-bar-wrap small {
  position: absolute;
  top: calc(100% + 7px);
  color: #8795a5;
  font-size: 9px;
  white-space: nowrap;
}

.chart-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  min-height: 58px;
  margin-top: 18px;
}

.activity-detail {
  display: grid;
  min-width: 0;
  color: #7d8c9d;
  font-size: 11px;
}

.activity-detail strong {
  margin: 2px 0;
  overflow: hidden;
  color: #19304c;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-detail small {
  overflow: hidden;
  color: #8c99a8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend {
  display: flex;
  align-items: center;
  flex: none;
  gap: 5px;
  color: #8d99a8;
  font-size: 10px;
}

.legend i {
  width: 11px;
  height: 11px;
  border-radius: 3px;
}

.breakdown {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e7eceb;
}

.breakdown-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: #1b304a;
  font-size: 13px;
  font-weight: 720;
}

.breakdown-heading small {
  color: #95a1af;
  font-size: 10px;
  font-weight: 500;
}

.breakdown-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 13px;
}

.breakdown-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 3px 8px;
  min-width: 0;
  padding: 10px 11px;
  border: 1px solid #e6ecea;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
}

.breakdown-item div {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
  color: #34485f;
  font-size: 11px;
}

.breakdown-item div i {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
}

.breakdown-item div span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.breakdown-item strong {
  color: #17304b;
  font-size: 12px;
}

.breakdown-item small {
  grid-column: 1 / -1;
  color: #8b98a7;
  font-size: 10px;
}

.product-assistant { background: #087457; }
.product-writing { background: #2c9f7b; }
.product-translation { background: #607eb2; }
.product-vocabulary { background: #8b6bb1; }
.product-other { background: #9aa7b4; }

.activity-skeleton {
  display: grid;
  grid-template-rows: repeat(3, 11px);
  grid-auto-flow: column;
  grid-auto-columns: 11px;
  gap: 5px;
  width: max-content;
  max-width: 100%;
  margin-top: 30px;
  overflow: hidden;
}

.activity-skeleton span {
  width: 11px;
  height: 11px;
  border-radius: 3px;
  background: #e8efed;
  animation: shimmer 1.4s ease-in-out infinite alternate;
}

.activity-state {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  margin-top: 24px;
  padding: 22px;
  border: 1px dashed #cedbd7;
  border-radius: 13px;
  color: #76879a;
  font-size: 12px;
}

.activity-state strong {
  margin-bottom: 5px;
  color: #22364f;
  font-size: 14px;
}

.activity-state button {
  margin-top: 13px;
  padding: 7px 12px;
  border: 1px solid #8ebdad;
  border-radius: 8px;
  background: #fff;
  color: #087457;
  font-weight: 680;
  cursor: pointer;
}

@keyframes shimmer {
  from { opacity: 0.45; }
  to { opacity: 1; }
}

@media (max-width: 760px) {
  .activity-card {
    padding: 19px 16px;
    border-radius: 15px;
  }

  .activity-header {
    align-items: flex-start;
  }

  .activity-description {
    max-width: 240px;
  }

  .activity-total span {
    font-size: 20px;
  }

  .breakdown-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .legend {
    align-self: flex-end;
  }
}
</style>
