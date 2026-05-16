<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input v-model="filters.keyword" class="admin-input" placeholder="搜索邮箱/手机号/昵称" @keyup.enter="load" />
          <input v-model="filters.userId" class="admin-input admin-input--sm" placeholder="请输入用户ID" @keyup.enter="load" />
          <select v-model="filters.status" class="admin-select">
            <option value="">全部状态</option>
            <option value="active">active</option>
            <option value="disabled">disabled</option>
          </select>
          <select v-model="filters.studyStage" class="admin-select">
            <option value="">全部学段</option>
            <option value="ielts">ielts</option>
            <option value="toefl">toefl</option>
            <option value="postgrad">postgrad</option>
            <option value="kaoyan">kaoyan</option>
          </select>
          <select v-model="filters.role" class="admin-select">
            <option value="">全部账号角色</option>
            <option value="user">user</option>
            <option value="admin">admin</option>
          </select>
          <select v-model="filters.adminRole" class="admin-select">
            <option value="">全部管理员角色</option>
            <option value="super_admin">super_admin</option>
            <option value="support_admin">support_admin</option>
            <option value="content_admin">content_admin</option>
          </select>
          <select v-model="filters.planCode" class="admin-select">
            <option value="">全部套餐</option>
            <option value="free">Free</option>
            <option value="basic">Basic</option>
            <option value="pro">Pro</option>
            <option value="premium">Premium</option>
          </select>
          <select v-model="filters.subscriptionStatus" class="admin-select">
            <option value="">全部订阅状态</option>
            <option value="free">free</option>
            <option value="active">active</option>
            <option value="expired">expired</option>
          </select>
          <select v-model="filters.overLimit" class="admin-select">
            <option value="">全部额度</option>
            <option value="true">已超额</option>
          </select>
          <input v-model="filters.createdFrom" class="admin-input admin-input--sm" type="date" aria-label="注册开始日期" />
          <input v-model="filters.createdTo" class="admin-input admin-input--sm" type="date" aria-label="注册结束日期" />
          <input v-model="filters.lastActiveFrom" class="admin-input admin-input--sm" type="date" aria-label="活跃开始日期" />
          <input v-model="filters.lastActiveTo" class="admin-input admin-input--sm" type="date" aria-label="活跃结束日期" />
        </div>
        <div class="admin-toolbar-right">
          <button class="admin-btn admin-btn--secondary" @click="resetFilters">重置</button>
          <button class="admin-btn" @click="search">查询</button>
        </div>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table admin-users-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户</th>
              <th>状态/学段</th>
              <th>角色</th>
              <th>订阅</th>
              <th>额度</th>
              <th>注册时间</th>
              <th>最近活跃</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id" class="admin-row-link" @click="openOverviewDrawer(item.id)">
              <td>{{ item.id }}</td>
              <td>
                <strong>{{ item.nickname || '-' }}</strong>
                <small>{{ item.email || item.phone || '-' }}</small>
              </td>
              <td>
                <strong>{{ item.status }}</strong>
                <small>{{ item.studyStage || '-' }}</small>
              </td>
              <td>
                <strong>{{ item.role || '-' }}</strong>
                <small>{{ item.adminRoles?.join(', ') || '-' }}</small>
              </td>
              <td>
                <strong>{{ item.planName || item.planCode || '-' }}</strong>
                <small>{{ item.subscriptionStatus || '-' }}</small>
              </td>
              <td>
                <strong>{{ formatQuota(item.tokenUsed) }} / {{ formatQuota(item.tokenLimit) }}</strong>
                <small>{{ item.quotaPeriod || '-' }} · 剩余 {{ formatQuota(item.tokenRemaining) }}{{ isOverLimit(item.overLimit) ? ' · 已超额' : '' }}</small>
              </td>
              <td>{{ item.createdAt || '-' }}</td>
              <td>{{ item.lastActiveAt || '-' }}</td>
              <td>
                <button class="admin-link-btn" @click.stop="goDetail(item.id)">查看详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="admin-pagination">
        <button class="admin-btn admin-btn--secondary" :disabled="page<=1" @click="page--; load()">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
        <button class="admin-btn admin-btn--secondary" :disabled="page * size >= total" @click="page++; load()">下一页</button>
      </div>
    </div>
    <aside v-if="drawerOpen" class="admin-user-drawer" aria-label="用户摘要抽屉">
      <div class="admin-user-drawer__header">
        <div>
          <div class="admin-user-drawer__title">{{ overview?.account.nickname || '用户摘要' }}</div>
          <div class="admin-user-drawer__sub">{{ overview?.account.email || overview?.account.phoneMasked || '-' }}</div>
        </div>
        <button class="admin-icon-btn" aria-label="关闭用户摘要抽屉" @click="closeDrawer">×</button>
      </div>

      <div v-if="drawerLoading" class="admin-drawer-state">正在加载用户摘要...</div>
      <div v-else-if="drawerError" class="admin-drawer-state admin-drawer-state--error">{{ drawerError }}</div>
      <template v-else-if="overview">
        <div class="admin-user-drawer__tags">
          <span class="admin-tag admin-tag--success">{{ overview.account.status }}</span>
          <span class="admin-tag">{{ overview.account.studyStage || '-' }}</span>
          <span v-for="role in overview.account.adminRoles" :key="role" class="admin-tag admin-tag--info">{{ role }}</span>
        </div>

        <section class="admin-drawer-card">
          <div class="admin-drawer-card__title">订阅与额度</div>
          <div class="admin-kv"><span>当前套餐</span><strong>{{ overview.subscription?.planName || overview.subscription?.planCode || '-' }}</strong></div>
          <div class="admin-kv"><span>额度使用</span><strong>{{ formatQuota(overview.subscription?.tokenUsed) }} / {{ formatQuota(overview.subscription?.tokenLimit) }}</strong></div>
          <div class="admin-quota-bar">
            <span :style="{ width: quotaPercent(overview.subscription) + '%' }"></span>
          </div>
          <div class="admin-drawer-muted">剩余 {{ formatQuota(overview.subscription?.tokenRemaining) }}</div>
        </section>

        <section class="admin-drawer-card">
          <div class="admin-drawer-card__title">
            <span>最近作文</span>
            <button class="admin-link-btn" @click="goPath(overview.quickLinks.essays)">查看作文</button>
          </div>
          <div v-if="overview.writing.recentEvaluations.length === 0" class="admin-drawer-muted">暂无最近作文</div>
          <div v-for="evaluation in overview.writing.recentEvaluations" :key="evaluation.id" class="admin-drawer-row">
            <span>作文 #{{ evaluation.id }}</span>
            <strong>{{ evaluation.gaokaoScore ?? evaluation.overallScore ?? '-' }} 分</strong>
          </div>
        </section>

        <section class="admin-drawer-card">
          <div class="admin-drawer-card__title">
            <span>AI 使用</span>
            <button class="admin-link-btn" @click="goPath(overview.quickLinks.aiUsage)">查看 AI</button>
          </div>
          <div class="admin-kv"><span>今日使用</span><strong>{{ formatQuota(overview.aiUsage.todayTokens) }} tokens</strong></div>
          <div class="admin-kv"><span>最近失败</span><strong>{{ overview.aiUsage.recentFailedRequests }}</strong></div>
        </section>

        <section class="admin-drawer-card">
          <div class="admin-drawer-card__title">
            <span>审计日志</span>
            <button class="admin-link-btn" @click="goPath(overview.quickLinks.auditLogs)">查看审计</button>
          </div>
          <div v-if="overview.audit.recentLogs.length === 0" class="admin-drawer-muted">暂无最近审计记录</div>
        </section>

        <div class="admin-user-drawer__actions">
          <button class="admin-btn" @click="goPath(overview.quickLinks.detail)">完整详情</button>
          <button class="admin-btn admin-btn--secondary" @click="goPath(overview.quickLinks.subscriptions)">查看订阅</button>
        </div>
      </template>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, type AdminUserListItem, type AdminUserOverview, type AdminUserSubscriptionSnapshot } from '@/api/admin'
import { showToast } from '@/utils/toast'

const router = useRouter()
const rows = ref<AdminUserListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const filters = ref(emptyFilters())
const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerError = ref('')
const overview = ref<AdminUserOverview | null>(null)

function emptyFilters() {
  return {
    userId: '',
    keyword: '',
    status: '',
    studyStage: '',
    role: '',
    adminRole: '',
    planCode: '',
    subscriptionStatus: '',
    overLimit: '',
    createdFrom: '',
    createdTo: '',
    lastActiveFrom: '',
    lastActiveTo: '',
  }
}

async function load() {
  try {
    const res = await adminApi.listUsers({ ...buildParams(), page: page.value, size })
    rows.value = res.items
    total.value = res.total
  } catch {
    showToast('加载用户列表失败', 'error')
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.value = emptyFilters()
  search()
}

function buildParams() {
  const params: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(filters.value)) {
    if (value !== '') params[key] = value
  }
  if (filters.value.userId) params.userId = Number(filters.value.userId)
  if (filters.value.overLimit === 'true') params.overLimit = true
  if (filters.value.createdFrom) params.createdFrom = `${filters.value.createdFrom} 00:00:00`
  if (filters.value.createdTo) params.createdTo = `${filters.value.createdTo} 23:59:59`
  if (filters.value.lastActiveFrom) params.lastActiveFrom = `${filters.value.lastActiveFrom} 00:00:00`
  if (filters.value.lastActiveTo) params.lastActiveTo = `${filters.value.lastActiveTo} 23:59:59`
  return params
}

function goDetail(id: number) {
  router.push(`/admin/users/${id}`)
}

async function openOverviewDrawer(id: number) {
  drawerOpen.value = true
  drawerLoading.value = true
  drawerError.value = ''
  overview.value = null
  try {
    overview.value = await adminApi.getUserOverview(id)
  } catch {
    drawerError.value = '加载用户摘要失败，请重试。'
  } finally {
    drawerLoading.value = false
  }
}

function closeDrawer() {
  drawerOpen.value = false
  overview.value = null
  drawerError.value = ''
}

function goPath(path: string) {
  router.push(path)
}

function formatQuota(value: number | string | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function isOverLimit(value: boolean | number | string | null | undefined) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

function quotaPercent(subscription: AdminUserSubscriptionSnapshot | null) {
  const used = Number(subscription?.tokenUsed ?? 0)
  const limit = Number(subscription?.tokenLimit ?? 0)
  if (limit <= 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

onMounted(load)
</script>

<style scoped>
.admin-users-table td strong,
.admin-users-table td small {
  display: block;
}

.admin-users-table td small {
  margin-top: 4px;
  color: var(--admin-muted);
  font-size: 12px;
  line-height: 1.45;
}

.admin-link-btn,
.admin-icon-btn {
  border: 0;
  background: transparent;
  color: var(--admin-accent);
  cursor: pointer;
  font: inherit;
  padding: 0;
}

.admin-icon-btn {
  color: var(--admin-muted);
  font-size: 22px;
  line-height: 1;
}

.admin-user-drawer {
  position: fixed;
  top: 86px;
  right: 24px;
  z-index: 30;
  width: min(420px, calc(100vw - 48px));
  max-height: calc(100vh - 112px);
  overflow: auto;
  padding: 18px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.16);
}

.admin-user-drawer__header,
.admin-drawer-card__title,
.admin-user-drawer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-user-drawer__title {
  font-size: 18px;
  font-weight: 700;
}

.admin-user-drawer__sub,
.admin-drawer-muted {
  margin-top: 4px;
  color: var(--admin-muted);
  font-size: 12px;
}

.admin-user-drawer__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 14px 0;
}

.admin-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 6px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 12px;
}

.admin-tag--success {
  background: #dcfce7;
  color: #15803d;
}

.admin-tag--info {
  background: #e0f2fe;
  color: #0369a1;
}

.admin-drawer-card {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
}

.admin-drawer-card__title {
  margin-bottom: 10px;
  font-weight: 700;
}

.admin-drawer-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-top: 1px solid var(--admin-border);
  font-size: 13px;
}

.admin-quota-bar {
  height: 6px;
  overflow: hidden;
  margin-top: 10px;
  border-radius: 999px;
  background: #e5e7eb;
}

.admin-quota-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--admin-accent);
}

.admin-drawer-state {
  padding: 24px 0;
  color: var(--admin-muted);
  text-align: center;
}

.admin-drawer-state--error {
  color: #b91c1c;
}

.admin-user-drawer__actions {
  justify-content: flex-start;
  margin-top: 14px;
}
</style>
