<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">订阅与用户运营</h1>
          <p class="admin-subtle">按每日新增、普通用户、订阅用户和 token 额度状态管理用户资产。</p>
        </div>
        <button class="admin-btn admin-btn--secondary" :disabled="overviewLoading" @click="loadOperationalData">
          刷新数据
        </button>
      </div>

      <div class="admin-overview-layout">
        <div>
          <div class="admin-grid-four">
            <div class="admin-stat">
              <div class="admin-stat-label">总用户</div>
              <div class="admin-stat-value">{{ formatNumber(overview.totalUsers) }}</div>
            </div>
            <div class="admin-stat">
              <div class="admin-stat-label">今日新增用户</div>
              <div class="admin-stat-value">{{ formatNumber(overview.todayNewUsers) }}</div>
            </div>
            <div class="admin-stat">
              <div class="admin-stat-label">订阅用户</div>
              <div class="admin-stat-value">{{ formatNumber(overview.subscribedUsers) }}</div>
            </div>
            <div class="admin-stat">
              <div class="admin-stat-label">普通用户</div>
              <div class="admin-stat-value">{{ formatNumber(overview.ordinaryUsers) }}</div>
            </div>
          </div>

          <div class="admin-grid-two admin-grid-two--compact">
            <div class="admin-kv">
              <span>今日新增订阅</span>
              <strong>{{ formatNumber(overview.todayNewSubscriptions) }}</strong>
            </div>
            <div class="admin-kv">
              <span>7 日订阅转化率</span>
              <strong>{{ formatPercent(overview.sevenDaySubscriptionRate) }}</strong>
            </div>
          </div>
        </div>

        <div class="admin-distribution-panel">
          <div class="admin-distribution-heading">
            <h2>订阅等级分布</h2>
            <span>{{ formatNumber(overview.totalUsers) }} 位用户</span>
          </div>
          <div ref="distributionChartEl" class="admin-distribution-chart" />
          <div class="admin-distribution-list">
            <div v-for="plan in overview.planDistribution" :key="plan.planCode" class="admin-distribution-row">
              <span>{{ plan.planName }}</span>
              <strong>{{ formatNumber(plan.userCount) }} 人 · {{ formatPercent(plan.ratio) }}</strong>
            </div>
            <div v-if="overview.planDistribution.length === 0" class="admin-empty-small">
              暂无等级分布数据。
            </div>
          </div>
        </div>
      </div>

      <div class="admin-debug-grid">
        <div class="admin-debug-panel">
          <div class="admin-debug-heading">
            <h2>数据库排查</h2>
            <span>users / admin_user_role</span>
          </div>
          <div class="admin-debug-kpis">
            <div>
              <span>用户表行数</span>
              <strong>{{ formatNumber(overview.userDiagnostics.databaseUserRows) }}</strong>
            </div>
            <div>
              <span>管理员账号</span>
              <strong>{{ formatNumber(overview.userDiagnostics.adminUsers) }}</strong>
            </div>
            <div>
              <span>active</span>
              <strong>{{ formatNumber(overview.userDiagnostics.activeUsers) }}</strong>
            </div>
            <div>
              <span>disabled</span>
              <strong>{{ formatNumber(overview.userDiagnostics.disabledUsers) }}</strong>
            </div>
          </div>
          <div class="admin-debug-foot">
            最新用户创建时间：{{ formatDateTime(overview.userDiagnostics.latestUserCreatedAt) }}
          </div>
        </div>

        <div class="admin-debug-panel">
          <div class="admin-debug-heading">
            <h2>管理员账号</h2>
            <span>{{ formatNumber(overview.userDiagnostics.adminUsers) }} 位</span>
          </div>
          <div class="admin-admin-preview">
            <div v-for="admin in overview.adminUserPreview" :key="admin.userId" class="admin-admin-preview-row">
              <div>
                <strong>{{ admin.nickname || admin.email || `ID ${admin.userId}` }}</strong>
                <span>{{ admin.email || '-' }}</span>
              </div>
              <div>
                <span>{{ admin.adminRoles.join(', ') || '-' }}</span>
                <span>{{ admin.status }} · {{ admin.studyStage || '-' }}</span>
              </div>
            </div>
            <div v-if="overview.adminUserPreview.length === 0" class="admin-empty-small">
              暂无管理员账号。
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">用户分层</h1>
          <p class="admin-subtle">普通用户按 Free 每日额度管理，订阅用户按付费套餐月额度管理。</p>
        </div>
        <span class="admin-badge">{{ total }} 位用户</span>
      </div>

      <div class="admin-segment-tabs">
        <button
          v-for="tab in segmentTabs"
          :key="tab.key"
          class="admin-segment-tab"
          :class="{ 'admin-segment-tab--active': activeSegment === tab.key }"
          @click="switchSegment(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.keyword" class="admin-input" placeholder="搜索用户 / 邮箱 / 手机号" @keyup.enter="search" />
          <select v-model="filters.planCode" class="admin-select">
            <option value="">全部套餐</option>
            <option v-for="rule in quotaRules" :key="rule.planCode" :value="rule.planCode">
              {{ rule.planName }}
            </option>
          </select>
          <input v-model="filters.expiresFrom" class="admin-input admin-input--sm" type="date" />
          <input v-model="filters.expiresTo" class="admin-input admin-input--sm" type="date" />
        </div>
        <button class="admin-btn" :disabled="loading" @click="search">查询</button>
      </div>

      <div v-if="loading" class="admin-loading">正在加载用户分层...</div>
      <div v-else-if="error" class="admin-error">
        {{ error }}
        <button class="admin-btn admin-btn--secondary" @click="load">重试</button>
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>用户类型</th>
              <th>套餐</th>
              <th>额度周期</th>
              <th>已用额度</th>
              <th>剩余额度</th>
              <th>到期时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.userId">
              <td>
                <strong>{{ item.nickname || '未命名用户' }}</strong>
                <div class="admin-subtle">{{ item.email || item.phone || `ID ${item.userId}` }}</div>
              </td>
              <td>
                <span class="admin-badge" :class="{ 'admin-badge--danger': Boolean(item.overLimit) }">
                  {{ userTypeLabel(item) }}
                </span>
              </td>
              <td>
                <strong>{{ item.planName || item.planCode }}</strong>
                <div class="admin-subtle">{{ item.planCode }}</div>
              </td>
              <td>{{ item.quotaPeriod === 'daily' ? '每日额度' : '每月额度' }}</td>
              <td>{{ formatTokens(item.tokenUsed) }}</td>
              <td>{{ formatTokens(item.tokenRemaining) }}</td>
              <td>{{ formatDateTime(item.currentPeriodEnd) }}</td>
            </tr>
            <tr v-if="items.length === 0" class="admin-empty-row">
              <td colspan="7">没有符合条件的用户。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-pagination">
        <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
        <div class="admin-toolbar-right">
          <button class="admin-btn admin-btn--secondary" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</button>
          <button class="admin-btn admin-btn--secondary" :disabled="page * size >= total || loading" @click="goPage(page + 1)">下一页</button>
        </div>
      </div>
    </div>

    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">每日用户数据</h1>
          <p class="admin-subtle">按天查看新增用户、订阅转化和普通/订阅用户 token 消耗。</p>
        </div>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>日期</th>
              <th>新增用户</th>
              <th>新增订阅</th>
              <th>订阅转化率</th>
              <th>普通用户</th>
              <th>订阅用户</th>
              <th>Free token</th>
              <th>付费 token</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in dailyStats" :key="row.statDate">
              <td>{{ row.statDate }}</td>
              <td>{{ formatNumber(row.newUsers) }}</td>
              <td>{{ formatNumber(row.newSubscriptions) }}</td>
              <td>{{ formatPercent(row.subscriptionRate) }}</td>
              <td>{{ formatNumber(row.ordinaryUsers) }}</td>
              <td>{{ formatNumber(row.subscribedUsers) }}</td>
              <td>{{ formatTokens(row.freeTokenUsed) }}</td>
              <td>{{ formatTokens(row.paidTokenUsed) }}</td>
            </tr>
            <tr v-if="dailyStats.length === 0" class="admin-empty-row">
              <td colspan="8">暂无每日用户数据。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">额度规则</h1>
          <p class="admin-subtle">Free 每日额度与付费套餐月度额度会立即影响后续 AI token quota check。</p>
        </div>
        <button class="admin-btn admin-btn--secondary" :disabled="quotaLoading" @click="loadQuotaRules">
          刷新规则
        </button>
      </div>
      <div v-if="quotaLoading" class="admin-loading">正在加载额度规则...</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>套餐</th>
              <th>额度周期</th>
              <th>规则额度</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rule in quotaRules" :key="rule.planCode">
              <td>
                <strong>{{ rule.planName }}</strong>
                <div class="admin-subtle">{{ rule.planCode }}</div>
              </td>
              <td>{{ rule.quotaPeriod === 'daily' ? '每日' : '每月' }}</td>
              <td>
                <input
                  v-model.number="quotaDrafts[rule.planCode]"
                  class="admin-input admin-input--quota"
                  type="number"
                  min="1"
                />
              </td>
              <td>
                <button
                  class="admin-btn"
                  :disabled="savingPlan === rule.planCode"
                  @click="saveQuotaRule(rule)"
                >
                  {{ savingPlan === rule.planCode ? '保存中...' : '保存' }}
                </button>
              </td>
            </tr>
            <tr v-if="quotaRules.length === 0" class="admin-empty-row">
              <td colspan="4">暂无额度规则。请确认后端已执行 subscription_plan 初始化或迁移。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  adminApi,
  type AdminSubscriptionDailyStat,
  type AdminSubscriptionListItem,
  type AdminSubscriptionOverview,
  type AdminSubscriptionAdminUserPreview,
  type AdminSubscriptionPlanDistribution,
  type AdminSubscriptionUserDiagnostics,
  type AdminSubscriptionQuotaRule,
} from '@/api/admin'
import { showToast } from '@/utils/toast'

echarts.use([PieChart, LegendComponent, TooltipComponent, CanvasRenderer])

type SegmentKey = 'all' | 'ordinary' | 'subscribed' | 'expired' | 'overLimit'

const size = 20
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const quotaLoading = ref(false)
const overviewLoading = ref(false)
const error = ref('')
const savingPlan = ref('')
const activeSegment = ref<SegmentKey>('all')
const items = ref<AdminSubscriptionListItem[]>([])
const quotaRules = ref<AdminSubscriptionQuotaRule[]>([])
const dailyStats = ref<AdminSubscriptionDailyStat[]>([])
const distributionChartEl = ref<HTMLElement | null>(null)
const distributionChart = useSubscriptionDistributionChart()
const quotaDrafts = reactive<Record<string, number>>({})

const overview = reactive<AdminSubscriptionOverview>({
  totalUsers: 0,
  ordinaryUsers: 0,
  subscribedUsers: 0,
  todayNewUsers: 0,
  todayNewSubscriptions: 0,
  todayFreeTokenUsed: 0,
  todayPaidTokenUsed: 0,
  overLimitUsers: 0,
  sevenDaySubscriptionRate: 0,
  planDistribution: [],
  userDiagnostics: emptyUserDiagnostics(),
  adminUserPreview: [],
})

const filters = reactive({
  keyword: '',
  planCode: '',
  expiresFrom: '',
  expiresTo: '',
})

const segmentTabs: Array<{ key: SegmentKey; label: string; status: string; overLimit?: boolean }> = [
  { key: 'all', label: '全部用户', status: '' },
  { key: 'ordinary', label: '普通用户', status: 'free' },
  { key: 'subscribed', label: '订阅用户', status: 'active' },
  { key: 'expired', label: '已过期', status: 'expired' },
  { key: 'overLimit', label: '已超额', status: '', overLimit: true },
]

async function loadOperationalData() {
  overviewLoading.value = true
  try {
    const [nextOverview, nextDailyStats] = await Promise.all([
      adminApi.getSubscriptionOverview(),
      adminApi.listSubscriptionDailyStats({}),
    ])
    Object.assign(overview, normalizeOverview(nextOverview))
    dailyStats.value = nextDailyStats.map(normalizeDailyStat)
  } catch {
    showToast('加载订阅运营数据失败', 'error')
  } finally {
    overviewLoading.value = false
  }
}

async function loadQuotaRules() {
  quotaLoading.value = true
  try {
    const rules = await adminApi.listSubscriptionQuotaRules()
    quotaRules.value = rules
    for (const rule of rules) {
      quotaDrafts[rule.planCode] = Number(rule.quotaPeriod === 'daily' ? rule.dailyTokenLimit : rule.monthlyTokenLimit) || 0
    }
  } catch {
    showToast('加载额度规则失败', 'error')
  } finally {
    quotaLoading.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  const tab = segmentTabs.find((item) => item.key === activeSegment.value) ?? segmentTabs[0]
  try {
    const data = await adminApi.listSubscriptions({
      ...filters,
      subscriptionStatus: tab.status,
      overLimit: tab.overLimit ? true : undefined,
      page: page.value,
      size,
    })
    items.value = data.items
    total.value = data.total
  } catch {
    error.value = '加载用户分层失败，请确认后端接口和数据库迁移已更新。'
    showToast('加载用户分层失败', 'error')
  } finally {
    loading.value = false
  }
}

async function saveQuotaRule(rule: AdminSubscriptionQuotaRule) {
  const nextLimit = Number(quotaDrafts[rule.planCode])
  if (!Number.isFinite(nextLimit) || nextLimit <= 0) {
    showToast('额度必须大于 0', 'error')
    return
  }
  savingPlan.value = rule.planCode
  try {
    await adminApi.updateSubscriptionQuotaRule(rule.planCode, rule.quotaPeriod === 'daily'
      ? { dailyTokenLimit: nextLimit }
      : { monthlyTokenLimit: nextLimit })
    showToast('额度规则已保存', 'success')
    await Promise.all([loadQuotaRules(), loadOperationalData(), load()])
  } catch {
    showToast('保存额度规则失败', 'error')
  } finally {
    savingPlan.value = ''
  }
}

function switchSegment(key: SegmentKey) {
  activeSegment.value = key
  page.value = 1
  void load()
}

function search() {
  page.value = 1
  void load()
}

function goPage(nextPage: number) {
  page.value = nextPage
  void load()
}

function userTypeLabel(item: AdminSubscriptionListItem) {
  if (item.overLimit) return '已超额'
  if (item.subscriptionStatus === 'active') return '订阅用户'
  if (item.subscriptionStatus === 'expired') return '已过期'
  return '普通用户'
}

function normalizeOverview(value: Partial<AdminSubscriptionOverview>): AdminSubscriptionOverview {
  return {
    totalUsers: Number(value.totalUsers ?? 0),
    ordinaryUsers: Number(value.ordinaryUsers ?? 0),
    subscribedUsers: Number(value.subscribedUsers ?? 0),
    todayNewUsers: Number(value.todayNewUsers ?? 0),
    todayNewSubscriptions: Number(value.todayNewSubscriptions ?? 0),
    todayFreeTokenUsed: Number(value.todayFreeTokenUsed ?? 0),
    todayPaidTokenUsed: Number(value.todayPaidTokenUsed ?? 0),
    overLimitUsers: Number(value.overLimitUsers ?? 0),
    sevenDaySubscriptionRate: Number(value.sevenDaySubscriptionRate ?? 0),
    planDistribution: normalizePlanDistribution(value.planDistribution ?? []),
    userDiagnostics: normalizeUserDiagnostics(value.userDiagnostics),
    adminUserPreview: normalizeAdminUserPreview(value.adminUserPreview ?? []),
  }
}

function normalizePlanDistribution(values: AdminSubscriptionPlanDistribution[]): AdminSubscriptionPlanDistribution[] {
  return values
    .map((value) => ({
      planCode: String(value.planCode ?? ''),
      planName: String(value.planName ?? value.planCode ?? ''),
      userCount: Number(value.userCount ?? 0),
      ratio: Number(value.ratio ?? 0),
      sortOrder: Number(value.sortOrder ?? 0),
    }))
    .sort((a, b) => a.sortOrder - b.sortOrder)
}

function emptyUserDiagnostics(): AdminSubscriptionUserDiagnostics {
  return {
    databaseUserRows: 0,
    activeUsers: 0,
    disabledUsers: 0,
    adminUsers: 0,
    regularUsers: 0,
    latestUserCreatedAt: null,
  }
}

function normalizeUserDiagnostics(value?: Partial<AdminSubscriptionUserDiagnostics>): AdminSubscriptionUserDiagnostics {
  return {
    databaseUserRows: Number(value?.databaseUserRows ?? 0),
    activeUsers: Number(value?.activeUsers ?? 0),
    disabledUsers: Number(value?.disabledUsers ?? 0),
    adminUsers: Number(value?.adminUsers ?? 0),
    regularUsers: Number(value?.regularUsers ?? 0),
    latestUserCreatedAt: value?.latestUserCreatedAt ? String(value.latestUserCreatedAt) : null,
  }
}

function normalizeAdminUserPreview(values: AdminSubscriptionAdminUserPreview[]): AdminSubscriptionAdminUserPreview[] {
  return values.map((value) => ({
    userId: Number(value.userId),
    email: value.email ? String(value.email) : null,
    nickname: value.nickname ? String(value.nickname) : null,
    status: String(value.status ?? '-'),
    studyStage: value.studyStage ? String(value.studyStage) : null,
    adminRoles: Array.isArray(value.adminRoles) ? value.adminRoles.map(String) : [],
    lastActiveAt: value.lastActiveAt ? String(value.lastActiveAt) : null,
  }))
}

function normalizeDailyStat(value: AdminSubscriptionDailyStat): AdminSubscriptionDailyStat {
  return {
    statDate: String(value.statDate),
    newUsers: Number(value.newUsers ?? 0),
    newSubscriptions: Number(value.newSubscriptions ?? 0),
    ordinaryUsers: Number(value.ordinaryUsers ?? 0),
    subscribedUsers: Number(value.subscribedUsers ?? 0),
    freeTokenUsed: Number(value.freeTokenUsed ?? 0),
    paidTokenUsed: Number(value.paidTokenUsed ?? 0),
    subscriptionRate: Number(value.subscriptionRate ?? 0),
  }
}

function formatNumber(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString()
}

function formatTokens(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString()
}

function formatPercent(value: number | null | undefined) {
  return `${Number(value ?? 0).toFixed(1)}%`
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

async function renderDistributionChart() {
  await nextTick()
  const rows = overview.planDistribution.filter((item) => item.userCount > 0)
  if (!distributionChartEl.value || rows.length === 0) {
    distributionChart.dispose()
    return
  }

  distributionChart.mount(distributionChartEl.value)
  distributionChart.setOption({
    color: ['#0f7a55', '#58a876', '#f0b75d', '#66769b'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 人 ({d}%)',
    },
    legend: {
      bottom: 0,
      textStyle: { color: '#607164' },
    },
    series: [{
      name: '订阅等级',
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['50%', '43%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fffdf7', borderWidth: 3 },
      label: { color: '#18261a', formatter: '{b}\n{d}%' },
      data: rows.map((item) => ({ name: item.planName, value: item.userCount })),
    }],
  })
}

function useSubscriptionDistributionChart() {
  const instance = shallowRef<echarts.ECharts | null>(null)

  function mount(element: HTMLElement) {
    if (!instance.value) {
      instance.value = echarts.init(element)
    }
  }

  function setOption(option: echarts.EChartsCoreOption) {
    instance.value?.setOption(option)
  }

  function resize() {
    instance.value?.resize()
  }

  function dispose() {
    instance.value?.dispose()
    instance.value = null
  }

  return { mount, setOption, resize, dispose }
}

function handleResize() {
  distributionChart.resize()
}

watch(() => overview.planDistribution, () => {
  void renderDistributionChart()
}, { deep: true })

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await Promise.all([loadOperationalData(), loadQuotaRules()])
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  distributionChart.dispose()
})
</script>

<style scoped>
.admin-overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: 24px;
  align-items: start;
}

.admin-grid-four {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.admin-grid-two--compact {
  margin-top: 14px;
}

.admin-debug-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(360px, 1.1fr);
  gap: 16px;
  margin-top: 18px;
}

.admin-debug-panel {
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: #fff;
  padding: 16px;
}

.admin-debug-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: var(--admin-muted);
}

.admin-debug-heading h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 18px;
}

.admin-debug-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.admin-debug-kpis div {
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--admin-surface-alt);
}

.admin-debug-kpis span,
.admin-debug-foot,
.admin-admin-preview-row span {
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-debug-kpis strong {
  display: block;
  margin-top: 8px;
  color: var(--admin-text);
  font-size: 22px;
}

.admin-debug-foot {
  margin-top: 12px;
}

.admin-admin-preview {
  display: grid;
  gap: 10px;
}

.admin-admin-preview-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(160px, auto);
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--admin-border);
}

.admin-admin-preview-row:last-child {
  border-bottom: none;
}

.admin-admin-preview-row div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.admin-admin-preview-row strong,
.admin-admin-preview-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-distribution-panel {
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: #fff;
  padding: 18px;
}

.admin-distribution-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: var(--admin-muted);
}

.admin-distribution-heading h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 18px;
}

.admin-distribution-chart {
  width: 100%;
  height: 260px;
}

.admin-distribution-list {
  display: grid;
  gap: 8px;
}

.admin-distribution-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--admin-muted);
  font-size: 14px;
}

.admin-distribution-row strong {
  color: var(--admin-text);
}

.admin-empty-small {
  color: var(--admin-muted);
  font-size: 14px;
}

.admin-segment-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 4px 0 18px;
}

.admin-segment-tab {
  border: 1px solid var(--admin-border);
  border-radius: 999px;
  padding: 8px 14px;
  background: #fff;
  color: var(--admin-muted);
  font-weight: 700;
}

.admin-segment-tab--active {
  background: var(--admin-accent);
  border-color: var(--admin-accent);
  color: #fff;
}

.admin-input--quota {
  max-width: 180px;
}

.admin-badge--danger {
  background: rgba(184, 77, 77, 0.12);
  color: var(--admin-danger);
}

@media (max-width: 1100px) {
  .admin-overview-layout {
    grid-template-columns: 1fr;
  }

  .admin-debug-grid {
    grid-template-columns: 1fr;
  }

  .admin-grid-four {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .admin-grid-four {
    grid-template-columns: 1fr;
  }

  .admin-debug-kpis,
  .admin-admin-preview-row {
    grid-template-columns: 1fr;
  }
}
</style>
