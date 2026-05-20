<template>
  <section class="admin-section model-usage-page">
    <header class="model-usage-hero">
      <div>
        <div class="model-usage-breadcrumb">‹ 模型用量 / Model Usage</div>
        <p class="model-usage-subtitle">统计项目和用户维度的 tokens、请求数、成本、失败率和延迟。</p>
      </div>
      <div class="model-usage-hero-actions" aria-label="管理员环境和操作">
        <label class="model-usage-env">
          <span>环境</span>
          <select class="admin-select" aria-label="环境">
            <option>Prod</option>
            <option>Staging</option>
          </select>
        </label>
        <button class="model-usage-icon-btn" type="button" aria-label="告警">!</button>
        <button class="model-usage-icon-btn" type="button" aria-label="设置">*</button>
        <div class="model-usage-admin-chip">
          <span>A</span>
          <strong>Admin</strong>
        </div>
      </div>
    </header>

    <div class="model-usage-tabs" aria-label="模型用量视图">
      <button class="model-usage-tab model-usage-tab--active" type="button">项目总览</button>
      <button class="model-usage-tab" type="button">用户用量</button>
    </div>

    <nav class="model-usage-anchor-nav" aria-label="模型用量页面区块导航">
      <button
        v-for="section in pageSections"
        :key="section.id"
        type="button"
        class="model-usage-anchor-tab"
        :class="{ 'model-usage-anchor-tab--active': activeSection === section.id }"
        @click="scrollToSection(section.id)"
      >
        {{ section.label }}
      </button>
    </nav>

    <section id="model-usage-filters" class="model-usage-filter-card model-usage-scroll-section" aria-label="多维度筛选与时间粒度">
      <div class="model-usage-filter-grid">
        <label class="model-usage-filter model-usage-filter--wide">
          <span>时间范围</span>
          <div class="model-usage-date-range">
            <select v-model="filters.timeRange" class="admin-select" aria-label="时间范围" @change="loadUsage">
              <option value="30d">最近 30 天</option>
              <option value="7d">最近 7 天</option>
              <option value="month">本月</option>
            </select>
            <input class="admin-input" type="text" :value="dateRangeLabel" readonly />
          </div>
        </label>
        <label class="model-usage-filter">
          <span>时间粒度</span>
          <select v-model="filters.granularity" class="admin-select" aria-label="时间粒度" @change="loadUsage">
            <option value="day">天</option>
            <option value="hour">小时</option>
            <option value="week">周</option>
            <option value="month">月</option>
          </select>
        </label>
        <label class="model-usage-filter">
          <span>Provider</span>
          <select v-model="filters.provider" class="admin-select" aria-label="Provider">
            <option value="">全部</option>
            <option v-for="option in filterOptions.providers" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>
        <label class="model-usage-filter">
          <span>模型</span>
          <select v-model="filters.model" class="admin-select" aria-label="模型" @change="loadUsage">
            <option value="">全部模型</option>
            <option v-for="option in filterOptions.models" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>
        <label class="model-usage-filter">
          <span>Workflow</span>
          <select v-model="filters.workflow" class="admin-select" aria-label="Workflow">
            <option value="">全部</option>
            <option v-for="option in filterOptions.workflows" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>
        <label class="model-usage-filter">
          <span>Agent</span>
          <select v-model="filters.agent" class="admin-select" aria-label="Agent" @change="loadUsage">
            <option value="">全部</option>
            <option v-for="option in filterOptions.agents" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>
        <label class="model-usage-filter">
          <span>用户</span>
          <input v-model="filters.userId" class="admin-input" placeholder="全部用户 / ID" aria-label="用户" @keyup.enter="loadUsage" />
        </label>
        <label class="model-usage-filter">
          <span>状态</span>
          <select v-model="filters.status" class="admin-select" aria-label="状态" @change="loadUsage">
            <option value="">全部</option>
            <option value="completed">成功</option>
            <option value="failed">失败</option>
            <option value="partial">部分完成</option>
          </select>
        </label>
        <div class="model-usage-filter-actions">
          <button class="admin-btn admin-btn--secondary" type="button" :disabled="loading" @click="loadUsage">
            {{ loading ? '刷新中' : '刷新' }}
          </button>
          <button class="admin-btn admin-btn--secondary" type="button" :disabled="events.length === 0" @click="exportCsv">导出 CSV</button>
        </div>
      </div>
    </section>

    <div v-if="error" class="model-usage-state model-usage-state--error">{{ error }}</div>
    <div v-else-if="loading" class="model-usage-state">正在加载后端模型用量数据...</div>

    <section id="model-usage-api-status" class="model-usage-card model-usage-api-status-card model-usage-scroll-section" aria-label="接口实现状态">
      <div class="model-usage-card-head">
        <div>
          <h2>接口实现状态</h2>
          <p>按当前页面能力标注后端真实接入程度，避免把前端聚合或占位字段误认为后端已完成。</p>
        </div>
      </div>
      <div class="model-usage-api-status-grid">
        <article v-for="item in apiStatusItems" :key="item.label" class="model-usage-api-status-item">
          <span class="model-usage-api-status-badge" :class="`model-usage-api-status-badge--${item.status}`">
            {{ apiStatusText[item.status] }}
          </span>
          <div>
            <strong>{{ item.label }}</strong>
            <p>{{ item.description }}</p>
          </div>
        </article>
      </div>
    </section>

    <section id="model-usage-kpis" class="model-usage-kpi-grid model-usage-scroll-section" aria-label="核心指标卡片">
      <article
        v-for="item in kpis"
        :key="item.label"
        class="model-usage-kpi-card"
        :class="`model-usage-kpi-card--${item.tone}`"
      >
        <span class="model-usage-kpi-icon">{{ item.icon }}</span>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
          <small>{{ item.delta }}</small>
        </div>
      </article>
    </section>

    <section id="model-usage-trend" class="model-usage-chart-card model-usage-scroll-section">
      <div class="model-usage-card-head">
        <div>
          <h2>Tokens 使用趋势</h2>
          <p>按 Provider 堆叠展示最近 30 天消耗，支持切换指标和分组。</p>
        </div>
        <div class="model-usage-chart-controls">
          <label>
            <span>指标</span>
            <select v-model="chartMetric" class="admin-select" aria-label="趋势指标">
              <option value="totalTokens">Total Tokens</option>
              <option value="requests">Requests</option>
              <option value="latencyMs">Latency</option>
            </select>
          </label>
          <label>
            <span>分组</span>
            <select v-model="breakdownDimension" class="admin-select" aria-label="趋势分组">
              <option value="provider">Provider</option>
              <option value="model">Model</option>
              <option value="workflow">Workflow</option>
              <option value="agent">Agent</option>
              <option value="user">User</option>
            </select>
          </label>
          <div class="model-usage-granularity" aria-label="时间粒度">
            <button type="button" :class="{ 'model-usage-granularity--active': filters.granularity === 'hour' }" @click="setGranularity('hour')">1h</button>
            <button type="button" :class="{ 'model-usage-granularity--active': filters.granularity === 'day' }" @click="setGranularity('day')">1d</button>
            <button type="button" :class="{ 'model-usage-granularity--active': filters.granularity === 'week' }" @click="setGranularity('week')">1w</button>
            <button type="button" :class="{ 'model-usage-granularity--active': filters.granularity === 'month' }" @click="setGranularity('month')">1M</button>
          </div>
        </div>
      </div>

      <div class="model-usage-legend" aria-label="Provider 图例">
        <span v-for="provider in providers" :key="provider.name">
          <i :style="{ background: provider.color }"></i>
          {{ provider.name }}
        </span>
      </div>

      <div class="model-usage-stacked-bars" aria-label="Token 趋势柱状图">
        <div class="model-usage-y-axis">
          <span>1.0M</span>
          <span>800K</span>
          <span>600K</span>
          <span>400K</span>
          <span>200K</span>
          <span>0</span>
        </div>
        <div class="model-usage-bars">
          <div v-for="bar in trendBars" :key="bar.date" class="model-usage-bar-column">
            <div class="model-usage-bar" :aria-label="`${bar.date} 总 Tokens ${bar.total}`">
              <span
                v-for="segment in bar.segments"
                :key="segment.provider"
                :style="{ height: `${segment.value}%`, background: segment.color }"
              ></span>
            </div>
            <small>{{ bar.date }}</small>
          </div>
          <div v-if="highlightedTrend" class="model-usage-tooltip" aria-hidden="true">
            <strong>{{ highlightedTrend.date }}</strong>
            <p v-for="segment in highlightedTrend.segments" :key="segment.provider">
              <span :style="{ background: segment.color }"></span>
              {{ segment.provider }} {{ formatNumber(segment.rawValue) }}
            </p>
            <strong>总计 {{ formatNumber(highlightedTrend.rawTotal) }}</strong>
          </div>
        </div>
      </div>
      <div class="model-usage-range-strip" aria-hidden="true">
        <span v-for="item in rangeStrip" :key="item" :style="{ height: `${item}%` }"></span>
      </div>
    </section>

    <div class="model-usage-insight-grid">
      <section id="model-usage-breakdown" class="model-usage-card model-usage-provider-table model-usage-scroll-section">
        <div class="model-usage-card-head">
          <h2>多维度拆解</h2>
          <nav aria-label="拆解维度">
            <button class="model-usage-tab model-usage-tab--active" type="button">Models</button>
            <button class="model-usage-tab" type="button">Providers</button>
            <button class="model-usage-tab" type="button">Workflows</button>
            <button class="model-usage-tab" type="button">Agents</button>
            <button class="model-usage-tab" type="button">Users</button>
            <button class="model-usage-tab" type="button">Events (明细)</button>
          </nav>
        </div>
        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>模型</th>
                <th>Provider</th>
                <th>总 Tokens</th>
                <th>输入 Tokens</th>
                <th>输出 Tokens</th>
                <th>请求数</th>
                <th>成本 (USD)</th>
                <th>平均延迟</th>
                <th>失败率</th>
                <th>趋势 (最近 30 天)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in modelRows" :key="row.model">
                <td>{{ row.model }}</td>
                <td>{{ row.provider }}</td>
                <td>{{ row.total }}</td>
                <td>{{ row.input }}</td>
                <td>{{ row.output }}</td>
                <td>{{ row.requests }}</td>
                <td>{{ row.cost }}</td>
                <td>{{ row.latency }}</td>
                <td>{{ row.failure }}</td>
                <td><span class="model-usage-sparkline"></span></td>
              </tr>
              <tr v-if="modelRows.length === 0" class="admin-empty-row">
                <td colspan="10">当前筛选条件下暂无后端 Agent run 用量数据。</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="model-usage-table-note">点击行可查看明细或加入对比</p>
      </section>

      <section class="model-usage-card model-usage-donut-card">
        <div class="model-usage-card-head">
          <h2>Tokens 分布 (按 Provider)</h2>
        </div>
        <div class="model-usage-donut-wrap">
          <div class="model-usage-donut" aria-label="Provider token 占比图">
            <div>
              <strong>12,564,382</strong>
              <span>总 Tokens</span>
            </div>
          </div>
          <ul class="model-usage-share-list">
            <li v-for="provider in providerShares" :key="provider.name">
              <span><i :style="{ background: provider.color }"></i>{{ provider.name }}</span>
              <strong>{{ provider.share }}</strong>
            </li>
          </ul>
        </div>
      </section>
    </div>

    <div class="model-usage-events-grid">
      <section id="model-usage-events" class="model-usage-card model-usage-events-card model-usage-scroll-section">
        <div class="model-usage-card-head">
          <h2>明细抽屉 (Events)</h2>
          <button class="admin-btn admin-btn--secondary" type="button">导出 CSV</button>
        </div>
        <div class="admin-table-wrap model-usage-events-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>用户</th>
                <th>Provider</th>
                <th>模型</th>
                <th>Workflow</th>
                <th>Agent</th>
                <th>输入 Tokens</th>
                <th>输出 Tokens</th>
                <th>成本 (USD)</th>
                <th>延迟</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="event in events" :key="event.runId">
                <td>{{ event.time }}</td>
                <td>{{ event.user }}</td>
                <td>{{ event.provider }}</td>
                <td>{{ event.model }}</td>
                <td>{{ event.workflow }}</td>
                <td>{{ event.agent }}</td>
                <td>{{ event.input }}</td>
                <td>{{ event.output }}</td>
                <td>{{ event.cost }}</td>
                <td>{{ event.latency }}</td>
                <td><button class="model-usage-link-btn" type="button" @click="openRun(event.runId)">Trace</button></td>
              </tr>
              <tr v-if="events.length === 0" class="admin-empty-row">
                <td colspan="11">当前筛选条件下暂无明细。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section id="model-usage-actions" class="model-usage-card model-usage-actions-card model-usage-scroll-section">
        <h2>操作</h2>
        <button type="button" :disabled="!events[0]" @click="events[0] && openRun(events[0].runId)">查看 Agent Run</button>
        <button type="button" :disabled="!events[0]" @click="events[0] && copyTrace(events[0].traceId)">查看 Trace</button>
      </section>

      <section class="model-usage-card model-usage-linkage-card">
        <h2>关联联动</h2>
        <div class="model-usage-linkage-flow">
          <div>Agent 调试中心</div>
          <span>→</span>
          <div>Token 明细 / Prompt / Usage</div>
        </div>
      </section>
    </div>

    <footer id="model-usage-sources" class="model-usage-footer-grid model-usage-scroll-section">
      <section class="model-usage-card">
        <h2>数据来源</h2>
        <div class="model-usage-source-flow">
          <span>模型调用 (Agent Run/Step)</span>
          <b>→</b>
          <span>Usage 解析</span>
          <b>→</b>
          <span>写入 model_usage_events</span>
          <b>→</b>
          <span>聚合到 model_usage_daily_aggregate</span>
          <b>→</b>
          <span>查询分析 / 展示</span>
        </div>
      </section>
      <section class="model-usage-card">
        <h2>支持能力</h2>
        <div class="model-usage-capabilities">
          <span>多模型</span>
          <span>多 Provider</span>
          <span>多时间粒度</span>
          <span>多维度分析</span>
          <span>成本计算</span>
          <span>告警监控</span>
        </div>
      </section>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { opsAgentApi, type AgentDebugRun, type AgentDebugRunDetail, type AgentRunQuery } from '@/api/opsAgent'

type Granularity = 'hour' | 'day' | 'week' | 'month'
type ChartMetric = 'totalTokens' | 'requests' | 'latencyMs'
type BreakdownDimension = 'provider' | 'model' | 'workflow' | 'agent' | 'user'
type ApiStatus = 'done' | 'partial' | 'todo'
type PageSectionId =
  | 'model-usage-filters'
  | 'model-usage-api-status'
  | 'model-usage-kpis'
  | 'model-usage-trend'
  | 'model-usage-breakdown'
  | 'model-usage-events'
  | 'model-usage-actions'
  | 'model-usage-sources'

interface UsageSummaryRun extends AgentDebugRun {
  provider: string
  inputTokens: number | null
  outputTokens: number | null
  requests: number
}

const route = useRoute()
const router = useRouter()

const runs = ref<AgentDebugRun[]>([])
const detailsByRunId = ref<Record<string, AgentDebugRunDetail>>({})
const total = ref(0)
const loading = ref(false)
const error = ref('')
const chartMetric = ref<ChartMetric>('totalTokens')
const breakdownDimension = ref<BreakdownDimension>('provider')
const activeSection = ref<PageSectionId>('model-usage-filters')

const pageSections: Array<{ id: PageSectionId; label: string }> = [
  { id: 'model-usage-filters', label: '筛选' },
  { id: 'model-usage-api-status', label: '接口状态' },
  { id: 'model-usage-kpis', label: '核心指标' },
  { id: 'model-usage-trend', label: 'Tokens 趋势' },
  { id: 'model-usage-breakdown', label: '多维拆解' },
  { id: 'model-usage-events', label: '明细事件' },
  { id: 'model-usage-actions', label: '操作联动' },
  { id: 'model-usage-sources', label: '数据来源' },
]

const filters = reactive({
  timeRange: '30d',
  granularity: 'day' as Granularity,
  provider: '',
  model: '',
  workflow: '',
  agent: '',
  userId: '',
  status: '',
})

const providerPalette = new Map([
  ['OpenAI', '#7367f0'],
  ['Anthropic', '#ffad66'],
  ['Google', '#65bf95'],
  ['DeepSeek', '#9b5de5'],
  ['Qwen', '#4bb5d8'],
  ['Kimi', '#366adf'],
  ['其他 / Local', '#6b8dd6'],
])

const apiStatusText: Record<ApiStatus, string> = {
  done: '已完成',
  partial: '部分完成',
  todo: '未完成',
}

const apiStatusItems: Array<{ status: ApiStatus; label: string; description: string }> = [
  {
    status: 'done',
    label: 'Agent run 列表',
    description: '已接入 GET /api/ops/agent/runs，提供 run、模型、workflow、agent、状态、延迟和 total tokens。',
  },
  {
    status: 'done',
    label: 'Run detail usage',
    description: '已接入 GET /api/ops/agent/runs/{runId}，用于补齐 input/output tokens 等 usage 明细。',
  },
  {
    status: 'partial',
    label: 'Provider 由 model 推断',
    description: '当前后端未返回 provider 字段，页面按 gpt/claude/gemini/deepseek/qwen/kimi 等模型名归类。',
  },
  {
    status: 'partial',
    label: '趋势与多维聚合',
    description: '当前由前端基于最多 100 条 run 聚合，后端还没有专用聚合接口或分页聚合结果。',
  },
  {
    status: 'partial',
    label: 'CSV 导出',
    description: '当前是前端导出已加载 run 数据，不是后端全量导出任务。',
  },
  {
    status: 'todo',
    label: '成本字段',
    description: '现有后端接口未返回 cost / price，页面成本相关位置显示占位。',
  },
  {
    status: 'todo',
    label: '后端聚合 API',
    description: '还没有按 Provider / Model / Workflow / Agent / User / 时间粒度聚合的模型用量接口。',
  },
]

const dateRange = computed(() => buildDateRange(filters.timeRange))
const dateRangeLabel = computed(() => `${dateRange.value.createdFrom.slice(0, 10)} → ${dateRange.value.createdTo.slice(0, 10)}`)

const backendRows = computed<UsageSummaryRun[]>(() => runs.value.map((run) => {
  const detail = detailsByRunId.value[run.runId]
  const usage = detail?.usage ?? {}
  return {
    ...run,
    provider: inferProvider(run.model),
    inputTokens: numberOrNull(usage.inputTokens),
    outputTokens: numberOrNull(usage.outputTokens),
    requests: numberOrNull(usage.requests) ?? 1,
  }
}))

const filteredRows = computed(() => backendRows.value.filter((row) => {
  if (filters.provider && row.provider !== filters.provider) return false
  if (filters.workflow && (row.workflow ?? '') !== filters.workflow) return false
  return true
}))

const providers = computed(() => groupRows('provider').map((item) => ({
  name: item.key,
  color: providerColor(item.key),
})))

const filterOptions = computed(() => ({
  providers: uniqueOptions(backendRows.value.map((row) => row.provider)),
  models: uniqueOptions(backendRows.value.map((row) => row.model)),
  workflows: uniqueOptions(backendRows.value.map((row) => row.workflow)),
  agents: uniqueOptions(backendRows.value.map((row) => row.targetAgent ?? row.agentName)),
}))

const totals = computed(() => {
  const totalTokens = sum(filteredRows.value, (row) => row.totalTokens)
  const inputTokens = sum(filteredRows.value, (row) => row.inputTokens)
  const outputTokens = sum(filteredRows.value, (row) => row.outputTokens)
  const requests = sum(filteredRows.value, (row) => row.requests)
  const failed = filteredRows.value.filter((row) => row.status && row.status !== 'completed').length
  const latencyRows = filteredRows.value.filter((row) => Number.isFinite(Number(row.latencyMs)))
  const avgLatencyMs = latencyRows.length ? Math.round(sum(latencyRows, (row) => row.latencyMs) / latencyRows.length) : 0
  return { totalTokens, inputTokens, outputTokens, requests, failed, avgLatencyMs }
})

const kpis = computed(() => [
  { label: '总 Tokens', value: formatNumber(totals.value.totalTokens), delta: backendHint.value, tone: 'blue', icon: 'T' },
  { label: '输入 Tokens', value: formatNumber(totals.value.inputTokens), delta: usageDetailHint.value, tone: 'indigo', icon: 'I' },
  { label: '输出 Tokens', value: formatNumber(totals.value.outputTokens), delta: usageDetailHint.value, tone: 'violet', icon: 'O' },
  { label: '请求数', value: formatNumber(totals.value.requests), delta: backendHint.value, tone: 'cyan', icon: 'R' },
  { label: '总成本 (USD)', value: '—', delta: '当前后端未返回成本字段', tone: 'purple', icon: '$' },
  { label: '失败率', value: formatPercent(totals.value.requests ? totals.value.failed / totals.value.requests : 0), delta: backendHint.value, tone: 'red', icon: '!' },
  { label: '平均延迟', value: totals.value.avgLatencyMs ? `${(totals.value.avgLatencyMs / 1000).toFixed(2)}s` : '—', delta: backendHint.value, tone: 'sky', icon: 'L' },
])

const trendBars = computed(() => {
  const buckets = new Map<string, Map<string, number>>()
  for (const row of filteredRows.value) {
    const date = bucketLabel(row.createdAt, filters.granularity)
    const group = breakdownKey(row, breakdownDimension.value)
    const value = metricValue(row, chartMetric.value)
    if (!buckets.has(date)) buckets.set(date, new Map())
    buckets.get(date)?.set(group, (buckets.get(date)?.get(group) ?? 0) + value)
  }

  const maxTotal = Math.max(1, ...Array.from(buckets.values()).map((groups) => sumMap(groups)))
  return Array.from(buckets.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, groups]) => {
      const rawTotal = sumMap(groups)
      const segments = Array.from(groups.entries())
        .sort(([, left], [, right]) => right - left)
        .map(([provider, rawValue]) => ({
          provider,
          rawValue,
          value: Math.max(4, (rawValue / maxTotal) * 90),
          color: groupColor(provider),
        }))
      return {
        date,
        total: formatCompact(rawTotal),
        rawTotal,
        segments,
      }
    })
})

const highlightedTrend = computed(() => trendBars.value[Math.floor(trendBars.value.length * 0.72)] ?? trendBars.value[0] ?? null)
const rangeStrip = computed(() => {
  const max = Math.max(1, ...trendBars.value.map((bar) => bar.rawTotal))
  return trendBars.value.map((bar) => Math.max(12, Math.round((bar.rawTotal / max) * 100)))
})

const modelRows = computed(() => groupRows('model').map((item) => ({
  model: item.key,
  provider: inferProvider(item.key),
  total: formatNumber(item.totalTokens),
  input: item.inputTokens > 0 ? formatNumber(item.inputTokens) : '—',
  output: item.outputTokens > 0 ? formatNumber(item.outputTokens) : '—',
  requests: formatNumber(item.requests),
  cost: '—',
  latency: item.avgLatencyMs ? `${(item.avgLatencyMs / 1000).toFixed(2)}s` : '—',
  failure: formatPercent(item.requests ? item.failed / item.requests : 0),
})))

const providerShares = computed(() => {
  const totalTokens = Math.max(1, totals.value.totalTokens)
  return groupRows('provider').map((item) => ({
    name: item.key,
    share: formatPercent(item.totalTokens / totalTokens),
    color: providerColor(item.key),
  }))
})

const events = computed(() => filteredRows.value.slice(0, 20).map((row) => ({
  runId: row.runId,
  traceId: row.traceId ?? '',
  time: row.createdAt ?? '-',
  user: row.userId ? `User ${row.userId}` : '-',
  provider: row.provider,
  model: row.model ?? '-',
  workflow: row.workflow ?? row.intent ?? '-',
  agent: row.targetAgent ?? row.agentName ?? '-',
  input: row.inputTokens == null ? '—' : formatNumber(row.inputTokens),
  output: row.outputTokens == null ? '—' : formatNumber(row.outputTokens),
  cost: '—',
  latency: row.latencyMs == null ? '—' : `${row.latencyMs}ms`,
})))

const backendHint = computed(() => `${formatNumber(total.value)} 条后端 Agent run`)
const usageDetailHint = computed(() => `已补齐 ${Object.keys(detailsByRunId.value).length} 条 usage 明细`)

function buildRunQuery(): AgentRunQuery {
  const range = dateRange.value
  return {
    status: filters.status,
    targetAgent: filters.agent,
    model: filters.model,
    userId: filters.userId,
    createdFrom: range.createdFrom,
    createdTo: range.createdTo,
    page: 1,
    size: 100,
  }
}

async function loadUsage() {
  loading.value = true
  error.value = ''
  try {
    const data = await opsAgentApi.listRuns(buildRunQuery())
    runs.value = data.items ?? []
    total.value = data.total ?? runs.value.length

    const detailResults = await Promise.allSettled(
      runs.value.slice(0, 30).map(async (run) => [run.runId, await opsAgentApi.getRun(run.runId)] as const),
    )
    detailsByRunId.value = Object.fromEntries(
      detailResults
        .filter((result): result is PromiseFulfilledResult<readonly [string, AgentDebugRunDetail]> => result.status === 'fulfilled')
        .map((result) => result.value),
    )
  } catch (err) {
    error.value = err instanceof Error ? err.message : '模型用量后端数据加载失败'
    runs.value = []
    detailsByRunId.value = {}
    total.value = 0
  } finally {
    loading.value = false
  }
}

function setGranularity(value: Granularity) {
  filters.granularity = value
  void loadUsage()
}

function scrollToSection(sectionId: PageSectionId) {
  activeSection.value = sectionId
  document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function updateActiveSection() {
  let current = pageSections[0].id
  for (const section of pageSections) {
    const element = document.getElementById(section.id)
    if (!element) continue
    if (element.getBoundingClientRect().top <= 132) current = section.id
  }
  activeSection.value = current
}

function openRun(runId: string) {
  void router.push(`/admin/agent-debug/runs/${encodeURIComponent(runId)}`)
}

async function copyTrace(traceId: string) {
  if (!traceId) return
  await navigator.clipboard?.writeText(traceId)
}

function exportCsv() {
  const header = ['time', 'user', 'provider', 'model', 'workflow', 'agent', 'input_tokens', 'output_tokens', 'total_tokens', 'latency']
  const rows = filteredRows.value.map((row) => [
    row.createdAt ?? '',
    row.userId ?? '',
    row.provider,
    row.model ?? '',
    row.workflow ?? row.intent ?? '',
    row.targetAgent ?? row.agentName ?? '',
    row.inputTokens ?? '',
    row.outputTokens ?? '',
    row.totalTokens ?? '',
    row.latencyMs ?? '',
  ])
  const csv = [header, ...rows]
    .map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(','))
    .join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'model-usage-agent-runs.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function groupRows(dimension: BreakdownDimension) {
  const grouped = new Map<string, UsageSummaryRun[]>()
  for (const row of filteredRows.value) {
    const key = breakdownKey(row, dimension)
    if (!grouped.has(key)) grouped.set(key, [])
    grouped.get(key)?.push(row)
  }
  return Array.from(grouped.entries()).map(([key, rowsForKey]) => {
    const requests = sum(rowsForKey, (row) => row.requests)
    const latencyRows = rowsForKey.filter((row) => Number.isFinite(Number(row.latencyMs)))
    return {
      key,
      totalTokens: sum(rowsForKey, (row) => row.totalTokens),
      inputTokens: sum(rowsForKey, (row) => row.inputTokens),
      outputTokens: sum(rowsForKey, (row) => row.outputTokens),
      requests,
      failed: rowsForKey.filter((row) => row.status && row.status !== 'completed').length,
      avgLatencyMs: latencyRows.length ? Math.round(sum(latencyRows, (row) => row.latencyMs) / latencyRows.length) : 0,
    }
  }).sort((left, right) => right.totalTokens - left.totalTokens)
}

function buildDateRange(range: string) {
  const now = new Date()
  const end = new Date(now)
  end.setHours(23, 59, 59, 999)
  const start = new Date(now)
  if (range === '7d') {
    start.setDate(now.getDate() - 6)
  } else if (range === 'month') {
    start.setDate(1)
  } else {
    start.setDate(now.getDate() - 29)
  }
  start.setHours(0, 0, 0, 0)
  return {
    createdFrom: toLocalDateTime(start),
    createdTo: toLocalDateTime(end),
  }
}

function toLocalDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function bucketLabel(value: string | null | undefined, granularity: Granularity) {
  if (!value) return 'unknown'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  if (granularity === 'hour') return `${month}-${day} ${String(date.getHours()).padStart(2, '0')}:00`
  if (granularity === 'month') return `${date.getFullYear()}-${month}`
  if (granularity === 'week') return `${date.getFullYear()}-W${weekOfYear(date)}`
  return `${month}-${day}`
}

function weekOfYear(date: Date) {
  const copy = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = copy.getUTCDay() || 7
  copy.setUTCDate(copy.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(copy.getUTCFullYear(), 0, 1))
  return String(Math.ceil((((copy.getTime() - yearStart.getTime()) / 86400000) + 1) / 7)).padStart(2, '0')
}

function inferProvider(model: string | null | undefined) {
  const lower = (model ?? '').toLowerCase()
  if (lower.includes('gpt') || lower.includes('openai')) return 'OpenAI'
  if (lower.includes('claude')) return 'Anthropic'
  if (lower.includes('gemini') || lower.includes('google')) return 'Google'
  if (lower.includes('deepseek')) return 'DeepSeek'
  if (lower.includes('qwen')) return 'Qwen'
  if (lower.includes('kimi')) return 'Kimi'
  return '其他 / Local'
}

function breakdownKey(row: UsageSummaryRun, dimension: BreakdownDimension) {
  if (dimension === 'provider') return row.provider
  if (dimension === 'model') return row.model || 'unknown'
  if (dimension === 'workflow') return row.workflow || row.intent || 'unknown'
  if (dimension === 'agent') return row.targetAgent || row.agentName || 'unknown'
  return row.userId == null ? 'unknown' : `User ${row.userId}`
}

function metricValue(row: UsageSummaryRun, metric: ChartMetric) {
  if (metric === 'requests') return row.requests
  if (metric === 'latencyMs') return numberOrNull(row.latencyMs) ?? 0
  return numberOrNull(row.totalTokens) ?? 0
}

function providerColor(provider: string) {
  return providerPalette.get(provider) ?? '#6b8dd6'
}

function groupColor(group: string) {
  return providerColor(group) || colorFromText(group)
}

function colorFromText(text: string) {
  const colors = ['#7367f0', '#ffad66', '#65bf95', '#9b5de5', '#4bb5d8', '#366adf', '#6b8dd6']
  const index = Math.abs(Array.from(text).reduce((sumValue, char) => sumValue + char.charCodeAt(0), 0)) % colors.length
  return colors[index]
}

function uniqueOptions(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.filter((value): value is string => Boolean(value && value.trim())))).sort()
}

function numberOrNull(value: unknown): number | null {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function sum<T>(items: T[], selector: (item: T) => unknown) {
  return items.reduce((totalValue, item) => totalValue + (numberOrNull(selector(item)) ?? 0), 0)
}

function sumMap(map: Map<string, number>) {
  return Array.from(map.values()).reduce((totalValue, value) => totalValue + value, 0)
}

function formatNumber(value: number | string | null | undefined) {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) ? new Intl.NumberFormat('zh-CN').format(Math.round(numberValue)) : '0'
}

function formatCompact(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${Math.round(value / 1_000)}K`
  return formatNumber(value)
}

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

watch(() => route.query.userId, (value) => {
  filters.userId = typeof value === 'string' ? value : ''
  void loadUsage()
}, { immediate: false })

onMounted(() => {
  filters.userId = typeof route.query.userId === 'string' ? route.query.userId : ''
  void loadUsage()
  updateActiveSection()
  window.addEventListener('scroll', updateActiveSection, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateActiveSection)
})
</script>

<style scoped>
.model-usage-page {
  gap: 14px;
  color: #17221a;
}

.model-usage-hero,
.model-usage-filter-card,
.model-usage-kpi-card,
.model-usage-chart-card,
.model-usage-card {
  border: 1px solid rgba(167, 184, 171, 0.48);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 34px rgba(31, 49, 38, 0.06);
}

.model-usage-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
}

.model-usage-breadcrumb {
  color: #0f1e15;
  font-size: 20px;
  font-weight: 800;
}

.model-usage-subtitle {
  margin-top: 4px;
  color: #586a60;
  font-size: 13px;
}

.model-usage-hero-actions,
.model-usage-filter-actions,
.model-usage-chart-controls,
.model-usage-legend,
.model-usage-card-head,
.model-usage-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.model-usage-env {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #29372f;
  font-size: 13px;
  font-weight: 700;
}

.model-usage-env .admin-select {
  width: 118px;
  padding: 8px 10px;
  border-radius: 6px;
}

.model-usage-icon-btn {
  width: 32px;
  height: 32px;
  border: 1px solid #d8e2d8;
  border-radius: 6px;
  background: #fff;
  color: #17221a;
  font-weight: 800;
}

.model-usage-admin-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.model-usage-admin-chip span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #bfe2d3;
  color: #0d6041;
  font-weight: 800;
}

.model-usage-tabs {
  gap: 8px;
}

.model-usage-anchor-nav {
  position: sticky;
  top: 0;
  z-index: 8;
  display: flex;
  gap: 18px;
  align-items: center;
  min-height: 48px;
  border-bottom: 1px solid rgba(167, 184, 171, 0.42);
  background: rgba(247, 251, 246, 0.96);
  overflow-x: auto;
  scrollbar-width: thin;
}

.model-usage-anchor-tab {
  position: relative;
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: #17221a;
  padding: 13px 0 14px;
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.model-usage-anchor-tab::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  border-radius: 999px;
  background: transparent;
  content: '';
}

.model-usage-anchor-tab--active {
  color: #1d6dff;
}

.model-usage-anchor-tab--active::after {
  background: #1d6dff;
}

.model-usage-scroll-section {
  scroll-margin-top: 62px;
}

.model-usage-tab {
  border: 0;
  border-radius: 6px;
  background: #eef2ef;
  color: #26352d;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 800;
}

.model-usage-tab--active {
  background: #17835b;
  color: #fff;
}

.model-usage-filter-card {
  padding: 14px;
}

.model-usage-state {
  border: 1px solid rgba(167, 184, 171, 0.48);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  padding: 12px 14px;
  color: #516459;
  font-size: 13px;
  font-weight: 700;
}

.model-usage-state--error {
  border-color: rgba(211, 71, 71, 0.28);
  background: #fff5f5;
  color: #b73a3a;
}

.model-usage-api-status-card {
  padding: 16px 18px;
}

.model-usage-api-status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.model-usage-api-status-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: start;
  min-height: 86px;
  border: 1px solid #d9e2d2;
  border-radius: 8px;
  background: #fbfdfb;
  padding: 12px;
}

.model-usage-api-status-item strong {
  display: block;
  color: #111b15;
  font-size: 13px;
  line-height: 1.25;
}

.model-usage-api-status-item p {
  margin-top: 5px;
  color: #5c6e63;
  font-size: 12px;
  line-height: 1.55;
}

.model-usage-api-status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  border-radius: 999px;
  padding: 5px 8px;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.model-usage-api-status-badge--done {
  background: #dcf2e6;
  color: #0f6b4a;
}

.model-usage-api-status-badge--partial {
  background: #fff3d8;
  color: #8b5a00;
}

.model-usage-api-status-badge--todo {
  background: #ffe6e6;
  color: #b73a3a;
}

.model-usage-filter-grid {
  display: grid;
  grid-template-columns: minmax(300px, 2fr) repeat(7, minmax(120px, 1fr)) auto;
  gap: 10px;
}

.model-usage-filter,
.model-usage-chart-controls label {
  display: grid;
  gap: 6px;
  color: #26352d;
  font-size: 12px;
  font-weight: 800;
}

.model-usage-filter .admin-select,
.model-usage-filter .admin-input,
.model-usage-chart-controls .admin-select {
  min-height: 38px;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
}

.model-usage-date-range {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px;
}

.model-usage-filter-actions {
  align-self: end;
  flex-wrap: nowrap;
}

.model-usage-filter-actions .admin-btn {
  min-height: 38px;
  border-radius: 6px;
  white-space: nowrap;
}

.model-usage-kpi-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 12px;
}

.model-usage-kpi-card {
  display: flex;
  gap: 12px;
  min-height: 96px;
  padding: 16px;
}

.model-usage-kpi-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: 8px;
  font-weight: 900;
}

.model-usage-kpi-card p {
  color: #516459;
  font-size: 12px;
  font-weight: 800;
}

.model-usage-kpi-card strong {
  display: block;
  margin-top: 4px;
  color: #111b15;
  font-size: 21px;
  line-height: 1.1;
}

.model-usage-kpi-card small {
  display: block;
  margin-top: 10px;
  color: #16824f;
  font-size: 12px;
  font-weight: 700;
}

.model-usage-kpi-card--blue .model-usage-kpi-icon,
.model-usage-kpi-card--sky .model-usage-kpi-icon {
  background: #e1f2ff;
  color: #2677d9;
}

.model-usage-kpi-card--indigo .model-usage-kpi-icon,
.model-usage-kpi-card--violet .model-usage-kpi-icon,
.model-usage-kpi-card--purple .model-usage-kpi-icon {
  background: #eee9ff;
  color: #6655da;
}

.model-usage-kpi-card--cyan .model-usage-kpi-icon {
  background: #e2f7fb;
  color: #118aa2;
}

.model-usage-kpi-card--red .model-usage-kpi-icon {
  background: #ffe9e9;
  color: #d34747;
}

.model-usage-kpi-card--red small,
.model-usage-kpi-card--sky small {
  color: #16824f;
}

.model-usage-chart-card,
.model-usage-card {
  padding: 18px;
}

.model-usage-card-head {
  justify-content: space-between;
  margin-bottom: 14px;
}

.model-usage-card-head h2,
.model-usage-card h2 {
  color: #111b15;
  font-size: 16px;
  font-weight: 800;
}

.model-usage-card-head p {
  margin-top: 4px;
  color: #6a7a70;
  font-size: 12px;
}

.model-usage-chart-controls {
  align-items: end;
}

.model-usage-chart-controls .admin-select {
  width: 160px;
}

.model-usage-granularity {
  display: flex;
  align-items: center;
  gap: 2px;
  min-height: 38px;
}

.model-usage-granularity button {
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #17221a;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 800;
}

.model-usage-granularity--active {
  background: #d8eadf !important;
  color: #116344 !important;
}

.model-usage-legend {
  margin-bottom: 12px;
  color: #29372f;
  font-size: 12px;
  font-weight: 700;
}

.model-usage-legend span,
.model-usage-share-list span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.model-usage-legend i,
.model-usage-share-list i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.model-usage-stacked-bars {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 8px;
  min-height: 240px;
}

.model-usage-y-axis {
  display: grid;
  grid-template-rows: repeat(6, 1fr);
  padding-bottom: 24px;
  color: #5e7065;
  font-size: 12px;
  text-align: right;
}

.model-usage-bars {
  position: relative;
  display: grid;
  grid-template-columns: repeat(30, minmax(16px, 1fr));
  align-items: end;
  gap: 10px;
  min-width: 920px;
  padding: 6px 4px 0;
  background:
    repeating-linear-gradient(to bottom, transparent 0, transparent 39px, #edf1ed 40px),
    linear-gradient(to bottom, transparent, transparent);
  overflow-x: auto;
}

.model-usage-bar-column {
  display: grid;
  gap: 8px;
  align-items: end;
}

.model-usage-bar {
  display: flex;
  flex-direction: column-reverse;
  justify-content: flex-start;
  height: 190px;
  overflow: hidden;
}

.model-usage-bar span {
  display: block;
  min-height: 4px;
}

.model-usage-bar-column small {
  color: #47574e;
  font-size: 11px;
  text-align: center;
  white-space: nowrap;
}

.model-usage-tooltip {
  position: absolute;
  right: 24%;
  top: 22px;
  display: grid;
  gap: 6px;
  width: 250px;
  padding: 14px;
  border-radius: 8px;
  background: #121614;
  color: #fff;
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.18);
  font-size: 12px;
}

.model-usage-tooltip p {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-usage-tooltip span {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.model-usage-range-strip {
  display: flex;
  align-items: end;
  gap: 2px;
  height: 28px;
  margin: 12px 8px 0 56px;
  padding: 4px;
  border: 1px solid #d7e4f7;
  border-radius: 6px;
  background: #f1f6ff;
}

.model-usage-range-strip span {
  flex: 1;
  border-radius: 2px;
  background: #c6dafb;
}

.model-usage-insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 2.3fr) minmax(320px, 0.8fr);
  gap: 14px;
}

.model-usage-provider-table .admin-table {
  min-width: 980px;
}

.model-usage-provider-table nav {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.model-usage-provider-table .model-usage-tab {
  padding: 7px 10px;
  background: transparent;
  color: #47574e;
}

.model-usage-provider-table .model-usage-tab--active {
  background: #e8f4ee;
  color: #0f6b4a;
}

.model-usage-sparkline {
  display: block;
  width: 96px;
  height: 28px;
  background:
    linear-gradient(135deg, transparent 8%, #7868f1 9%, #7868f1 13%, transparent 14%),
    linear-gradient(25deg, transparent 18%, #7868f1 19%, #7868f1 24%, transparent 25%),
    linear-gradient(150deg, transparent 42%, #7868f1 43%, #7868f1 48%, transparent 49%),
    linear-gradient(25deg, transparent 68%, #7868f1 69%, #7868f1 74%, transparent 75%);
}

.model-usage-table-note {
  margin-top: 10px;
  color: #17221a;
  font-size: 12px;
  font-weight: 800;
  text-align: center;
}

.model-usage-donut-wrap {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 20px;
  align-items: center;
}

.model-usage-donut {
  display: grid;
  place-items: center;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: conic-gradient(#7367f0 0 52.3%, #ff8f70 52.3% 82%, #4bb5d8 82% 92%, #9b5de5 92% 98%, #6b8dd6 98% 100%);
}

.model-usage-donut > div {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: #fff;
  color: #111b15;
  text-align: center;
}

.model-usage-donut strong {
  font-size: 17px;
}

.model-usage-donut span {
  color: #47574e;
  font-size: 12px;
}

.model-usage-share-list {
  display: grid;
  gap: 12px;
  list-style: none;
}

.model-usage-share-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #29372f;
  font-size: 13px;
}

.model-usage-events-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(140px, 0.3fr) minmax(360px, 1fr);
  gap: 14px;
}

.model-usage-events-card .admin-table {
  min-width: 1040px;
}

.model-usage-events-table-wrap {
  max-height: 360px;
  overflow: auto;
}

.model-usage-events-table-wrap .admin-table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #fff;
}

.model-usage-events-card .admin-table th,
.model-usage-events-card .admin-table td {
  white-space: nowrap;
}

.model-usage-actions-card {
  display: grid;
  align-content: center;
  gap: 12px;
}

.model-usage-actions-card button {
  border: 1px solid #d9e2d2;
  border-radius: 6px;
  background: #fff;
  padding: 10px;
  color: #17221a;
  font-weight: 800;
}

.model-usage-linkage-card {
  display: grid;
  align-content: center;
  gap: 14px;
}

.model-usage-linkage-flow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.model-usage-linkage-flow div {
  min-height: 86px;
  border: 1px solid #d9e2d2;
  border-radius: 8px;
  background: #f8fbf8;
  padding: 12px;
  color: #17221a;
  font-size: 13px;
  font-weight: 800;
}

.model-usage-link-btn {
  border: 0;
  background: transparent;
  color: #136f4b;
  font-weight: 800;
}

.model-usage-footer-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 14px;
}

.model-usage-source-flow,
.model-usage-capabilities {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 16px;
  color: #29372f;
  font-size: 13px;
  font-weight: 800;
}

.model-usage-source-flow span,
.model-usage-capabilities span {
  border: 1px solid #d9e2d2;
  border-radius: 999px;
  background: #f8fbf8;
  padding: 8px 12px;
}

.model-usage-source-flow b {
  color: #16824f;
}

@media (max-width: 1500px) {
  .model-usage-filter-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .model-usage-filter--wide,
  .model-usage-filter-actions {
    grid-column: span 2;
  }

  .model-usage-kpi-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .model-usage-hero,
  .model-usage-card-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .model-usage-filter-grid,
  .model-usage-api-status-grid,
  .model-usage-insight-grid,
  .model-usage-events-grid,
  .model-usage-footer-grid {
    grid-template-columns: 1fr;
  }

  .model-usage-filter--wide,
  .model-usage-filter-actions {
    grid-column: auto;
  }

  .model-usage-kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .model-usage-date-range,
  .model-usage-donut-wrap,
  .model-usage-linkage-flow {
    grid-template-columns: 1fr;
  }

  .model-usage-kpi-grid {
    grid-template-columns: 1fr;
  }

  .model-usage-tooltip {
    display: none;
  }
}
</style>
