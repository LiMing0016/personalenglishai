<template>
  <div class="admin-section admin-dashboard-page">
    <DashboardFilterBar v-model="filters" />

    <div class="admin-card admin-dashboard-page__hint">
      <div class="admin-dashboard-page__hint-row">
        <div>
          <div class="admin-card-title">Dashboard</div>
          <div class="admin-subtle">当前为前端演示数据。后端就位后将按模块逐步替换为真实接口。</div>
          <div class="admin-dashboard-page__meta">{{ filterSummary }}</div>
        </div>
        <DashboardBadge :label="payload?.meta.dataSource === 'mock' ? '示例数据' : '实时数据'" tone="warn" />
      </div>
      <div v-if="hiddenModules.length" class="admin-dashboard-page__notice">当前账号仅显示有权限的模块：{{ hiddenModules.join('、') }}</div>
    </div>

    <div class="admin-card admin-dashboard-page__quicklinks">
      <div class="admin-dashboard-page__quicklinks-head">
        <div class="admin-card-title">快捷入口</div>
        <div class="admin-subtle">先看数据，再进入具体治理页面。</div>
      </div>
      <div class="admin-inline-list">
        <router-link
          v-for="item in quickLinks"
          :key="item.to"
          :to="item.to"
          class="admin-badge admin-dashboard-page__quicklink"
        >{{ item.label }}</router-link>
      </div>
    </div>

    <div v-if="!hasAnyModuleAccess" class="admin-card admin-empty">当前账号暂无用户、作文、内容或审计模块权限，请联系超级管理员分配角色。</div>

    <div class="admin-grid-three admin-dashboard-summary-grid">
      <DashboardKpiCard label="累计用户" :value="formatNumber(payload?.summary.totalUsers ?? 0)" />
      <DashboardKpiCard label="今日新增用户" :value="formatNumber(payload?.summary.dailyNewUsers ?? 0)" />
      <DashboardKpiCard label="活跃用户" :value="formatNumber(payload?.summary.activeUsers ?? 0)" />
      <DashboardKpiCard label="写作提交" :value="formatNumber(payload?.summary.writingSubmissions ?? 0)" />
      <DashboardKpiCard label="平均分" :value="formatScore(payload?.summary.averageScore ?? 0)" />
      <DashboardKpiCard label="待处理任务" :value="formatNumber(payload?.summary.pendingTasks ?? 0)" />
    </div>

    <DashboardSection v-if="canViewUsers" title="用户增长" description="用户趋势与学段分布先使用前端 mock 数据呈现。" :status="usersStatus">
      <div class="admin-grid-two">
        <div class="admin-dashboard-list-card">
          <div class="admin-dashboard-list-card__title">最近趋势</div>
          <div class="admin-stack">
            <div v-for="item in payload?.users.trend.slice(-7) ?? []" :key="item.date" class="admin-kv">
              <span>{{ item.date }}</span>
              <strong>{{ formatNumber(item.value) }}</strong>
            </div>
          </div>
        </div>
        <div class="admin-dashboard-list-card">
          <div class="admin-dashboard-list-card__title">学段分布</div>
          <div class="admin-stack">
            <div v-for="item in payload?.users.stageBreakdown ?? []" :key="item.key" class="admin-kv">
              <span>{{ item.label }}</span>
              <strong>{{ formatNumber(item.value) }} / {{ formatPercent(item.ratio) }}</strong>
            </div>
          </div>
        </div>
      </div>
    </DashboardSection>

    <SubscriptionMetricsPanel :metrics="payload?.subscription ?? emptySubscription" :status="subscriptionStatus" />

    <div class="admin-grid-two admin-dashboard-split">
      <DashboardSection v-if="canViewWriting" title="写作质量" description="首版保留关键分布和趋势，后续再接真实聚合接口。" :status="writingStatus">
        <div class="admin-grid-two">
          <div class="admin-dashboard-list-card">
            <div class="admin-dashboard-list-card__title">近 7 日提交量</div>
            <div class="admin-stack">
              <div v-for="item in payload?.writing.submissionTrend.slice(-7) ?? []" :key="item.date" class="admin-kv">
                <span>{{ item.date }}</span>
                <strong>{{ formatNumber(item.value) }}</strong>
              </div>
            </div>
          </div>
          <div class="admin-dashboard-list-card">
            <div class="admin-dashboard-list-card__title">分数段分布</div>
            <div class="admin-stack">
              <div v-for="item in payload?.writing.scoreBreakdown ?? []" :key="item.key" class="admin-kv">
                <span>{{ item.label }}</span>
                <strong>{{ formatNumber(item.value) }} / {{ formatPercent(item.ratio) }}</strong>
              </div>
            </div>
          </div>
        </div>
      </DashboardSection>

      <ModelUsagePanel :metrics="payload?.modelUsage ?? emptyModelUsage" :status="modelUsageStatus" />
    </div>

    <div class="admin-grid-two admin-dashboard-split">
      <DashboardSection v-if="canViewContent" title="内容治理" description="题库与 Rubric 当前以轻量概览呈现。" :status="contentStatus">
        <div class="admin-grid-two admin-dashboard-kpi-grid">
          <DashboardKpiCard label="题库总量" :value="formatNumber(payload?.content.totalPrompts ?? 0)" />
          <DashboardKpiCard label="启用题目" :value="formatNumber(payload?.content.activePrompts ?? 0)" />
          <DashboardKpiCard label="Rubric 版本" :value="formatNumber(payload?.content.totalRubrics ?? 0)" />
          <DashboardKpiCard label="激活 Rubric" :value="formatNumber(payload?.content.activeRubrics ?? 0)" />
        </div>
      </DashboardSection>

      <DashboardSection v-if="canViewAudit" title="审计活动" description="审计区块按权限裁剪显示。" :status="auditStatus">
        <div class="admin-grid-two">
          <div class="admin-dashboard-list-card">
            <div class="admin-dashboard-list-card__title">动作分布</div>
            <div class="admin-stack">
              <div v-for="item in payload?.audit.actionBreakdown ?? []" :key="item.key" class="admin-kv">
                <span>{{ item.label }}</span>
                <strong>{{ formatNumber(item.value) }} / {{ formatPercent(item.ratio) }}</strong>
              </div>
            </div>
          </div>
          <div class="admin-dashboard-list-card">
            <div class="admin-dashboard-list-card__title">最近趋势</div>
            <div class="admin-stack">
              <div v-for="item in payload?.audit.dailyActions.slice(-7) ?? []" :key="item.date" class="admin-kv">
                <span>{{ item.date }}</span>
                <strong>{{ formatNumber(item.value) }}</strong>
              </div>
            </div>
          </div>
        </div>
      </DashboardSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getAdminMe, type AdminMe } from '@/api/admin'
import { showToast } from '@/utils/toast'
import DashboardBadge from '../components/DashboardBadge.vue'
import DashboardFilterBar from '../components/DashboardFilterBar.vue'
import DashboardKpiCard from '../components/DashboardKpiCard.vue'
import DashboardSection from '../components/DashboardSection.vue'
import ModelUsagePanel from '../components/ModelUsagePanel.vue'
import SubscriptionMetricsPanel from '../components/SubscriptionMetricsPanel.vue'
import { adminDashboardApi } from '../api/adminDashboardApi'
import type {
  AdminDashboardFilter,
  AdminDashboardPayload,
  AdminDashboardStatus,
  AdminModelUsageMetrics,
  AdminSubscriptionMetrics,
} from '../types'

const filters = ref<AdminDashboardFilter>({
  preset: 'thisMonth',
  timezone: 'Asia/Shanghai',
})
const payload = ref<AdminDashboardPayload | null>(null)
const me = ref<AdminMe | null>(null)
const pageStatus = ref<AdminDashboardStatus>('loading')

const emptySubscription: AdminSubscriptionMetrics = {
  dailyNew: 0,
  yesterdayNew: 0,
  monthlyNew: 0,
  totalSubscribedUsers: 0,
  activeSubscribers: 0,
  trend: [],
  planBreakdown: [],
}

const emptyModelUsage: AdminModelUsageMetrics = {
  totalInputTokens: 0,
  totalOutputTokens: 0,
  totalTokens: 0,
  estimatedCost: 0,
  chart: [],
  table: [],
}

const permissions = computed(() => new Set(me.value?.permissions ?? []))
const canViewUsers = computed(() => permissions.value.has('admin.users.read'))
const canViewWriting = computed(() => permissions.value.has('admin.essays.read'))
const canViewContent = computed(() => permissions.value.has('admin.prompts.read') || permissions.value.has('admin.rubrics.read'))
const canViewAudit = computed(() => permissions.value.has('admin.audit.read'))

const usersStatus = computed<AdminDashboardStatus>(() => sectionStatus(canViewUsers.value, payload.value?.users.trend.length ?? 0))
const subscriptionStatus = computed<AdminDashboardStatus>(() => sectionStatus(true, payload.value?.subscription.trend.length ?? 0))
const writingStatus = computed<AdminDashboardStatus>(() => sectionStatus(canViewWriting.value, payload.value?.writing.submissionTrend.length ?? 0))
const modelUsageStatus = computed<AdminDashboardStatus>(() => sectionStatus(true, payload.value?.modelUsage.chart.length ?? 0))
const contentStatus = computed<AdminDashboardStatus>(() => sectionStatus(canViewContent.value, payload.value ? 1 : 0))
const auditStatus = computed<AdminDashboardStatus>(() => sectionStatus(canViewAudit.value, payload.value?.audit.dailyActions.length ?? 0))
const hasAnyModuleAccess = computed(() => canViewUsers.value || canViewWriting.value || canViewContent.value || canViewAudit.value)
const hiddenModules = computed(() => {
  const items: string[] = []
  if (!canViewUsers.value) items.push('用户增长')
  if (!canViewWriting.value) items.push('写作质量')
  if (!canViewContent.value) items.push('内容治理')
  if (!canViewAudit.value) items.push('审计活动')
  return items
})
const quickLinks = computed(() => {
  const links = [{ to: '/admin/dashboard', label: 'Dashboard' }]
  if (canViewUsers.value) links.push({ to: '/admin/users', label: '用户列表' })
  if (canViewWriting.value) links.push({ to: '/admin/essays', label: '作文排查' })
  if (canViewContent.value) links.push({ to: '/admin/prompts', label: '题库管理' })
  if (canViewContent.value) links.push({ to: '/admin/rubrics', label: 'Rubric 管理' })
  if (canViewAudit.value) links.push({ to: '/admin/audit-logs', label: '审计日志' })
  return links
})
const filterSummary = computed(() => {
  const meta = payload.value?.meta.filters
  if (!meta?.startDate || !meta?.endDate) return '统计区间：加载中'
  return `统计区间：${meta.startDate} 至 ${meta.endDate}（${meta.timezone ?? 'Asia/Shanghai'}）`
})

function sectionStatus(visible: boolean, size: number): AdminDashboardStatus {
  if (pageStatus.value === 'loading') return 'loading'
  if (pageStatus.value === 'error') return 'error'
  if (!visible) return 'empty'
  return size > 0 ? 'ready' : 'empty'
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatScore(value: number) {
  return Number(value).toFixed(1)
}

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

async function load() {
  pageStatus.value = 'loading'
  try {
    me.value = await getAdminMe()
    payload.value = await adminDashboardApi.getDashboard(filters.value)
    pageStatus.value = 'ready'
  } catch {
    pageStatus.value = 'error'
    showToast('Dashboard 加载失败', 'error')
  }
}

watch(filters, load, { deep: true })

onMounted(load)
</script>
