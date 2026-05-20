<template>
  <section class="admin-section admin-users-center">
    <div class="admin-users-shell">
      <div class="admin-users-main">
        <div class="admin-page-heading">
          <div>
            <h1>用户中心</h1>
            <p>/ admin/users</p>
          </div>
          <div class="admin-page-heading__actions">
            <button class="admin-btn" :disabled="loading" @click="search">
              {{ loading ? '查询中' : '查询' }}
            </button>
            <button class="admin-btn admin-btn--secondary" :disabled="loading" @click="resetFilters">
              重置
            </button>
          </div>
        </div>

        <div class="admin-card admin-users-filter-card">
          <label class="admin-filter-field admin-filter-field--wide">
            <span>关键词</span>
            <input v-model="filters.keyword" class="admin-input" placeholder="昵称 / 邮箱 / 手机号" @keyup.enter="search" />
          </label>
          <label class="admin-filter-field">
            <span>用户ID</span>
            <input v-model="filters.userId" class="admin-input" placeholder="请输入用户ID" @keyup.enter="search" />
          </label>
          <label class="admin-filter-field">
            <span>状态</span>
            <select v-model="filters.status" class="admin-select">
              <option value="">全部</option>
              <option value="active">active</option>
              <option value="disabled">disabled</option>
            </select>
          </label>
          <label class="admin-filter-field">
            <span>学段</span>
            <select v-model="filters.studyStage" class="admin-select">
              <option value="">全部</option>
              <option value="ielts">ielts</option>
              <option value="toefl">toefl</option>
              <option value="postgrad">postgrad</option>
              <option value="kaoyan">kaoyan</option>
              <option value="gaokao">gaokao</option>
            </select>
          </label>
          <label class="admin-filter-field">
            <span>账号角色</span>
            <select v-model="filters.role" class="admin-select">
              <option value="">全部</option>
              <option value="user">user</option>
              <option value="admin">admin</option>
            </select>
          </label>
          <label class="admin-filter-field">
            <span>管理员角色</span>
            <select v-model="filters.adminRole" class="admin-select">
              <option value="">全部</option>
              <option value="super_admin">super_admin</option>
              <option value="support_admin">support_admin</option>
              <option value="content_admin">content_admin</option>
            </select>
          </label>
          <label class="admin-filter-field">
            <span>当前套餐</span>
            <select v-model="filters.planCode" class="admin-select">
              <option value="">全部</option>
              <option value="free">Free</option>
              <option value="basic">Basic</option>
              <option value="pro">Pro</option>
              <option value="premium">Premium</option>
            </select>
          </label>
          <label class="admin-filter-field">
            <span>订阅状态</span>
            <select v-model="filters.subscriptionStatus" class="admin-select">
              <option value="">全部</option>
              <option value="free">free</option>
              <option value="active">active</option>
              <option value="expired">expired</option>
            </select>
          </label>
          <label class="admin-filter-field">
            <span>是否超额</span>
            <select v-model="filters.overLimit" class="admin-select">
              <option value="">全部</option>
              <option value="true">已超额</option>
            </select>
          </label>
          <label class="admin-filter-field admin-filter-field--range">
            <span>注册时间</span>
            <div class="admin-date-range">
              <input v-model="filters.createdFrom" class="admin-input" type="date" aria-label="注册开始日期" />
              <span>~</span>
              <input v-model="filters.createdTo" class="admin-input" type="date" aria-label="注册结束日期" />
            </div>
          </label>
          <label class="admin-filter-field admin-filter-field--range">
            <span>最近活跃</span>
            <div class="admin-date-range">
              <input v-model="filters.lastActiveFrom" class="admin-input" type="date" aria-label="活跃开始日期" />
              <span>~</span>
              <input v-model="filters.lastActiveTo" class="admin-input" type="date" aria-label="活跃结束日期" />
            </div>
          </label>
        </div>

        <div class="admin-card admin-users-table-card">
          <div v-if="loading" class="admin-users-state">正在加载用户列表...</div>
          <div v-else-if="rows.length === 0" class="admin-users-state">暂无匹配用户</div>
          <div v-else class="admin-table-wrap">
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
                <tr
                  v-for="item in rows"
                  :key="item.id"
                  class="admin-row-link"
                  :class="{ 'admin-row-link--selected': selectedUserId === item.id }"
                  @click="openOverviewDrawer(item.id)"
                >
                  <td>{{ item.id }}</td>
                  <td>
                    <div class="admin-user-cell">
                      <span class="admin-avatar">{{ initials(item.nickname, item.id) }}</span>
                      <div>
                        <strong>{{ item.nickname || '-' }}</strong>
                        <small>{{ item.email || item.phone || '-' }}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="admin-tag" :class="statusTagClass(item.status)">{{ item.status }}</span>
                    <small>{{ item.studyStage || '-' }}</small>
                  </td>
                  <td>
                    <strong>{{ item.role || '-' }}</strong>
                    <small>{{ item.adminRoles?.join(', ') || '-' }}</small>
                  </td>
                  <td>
                    <strong>{{ item.planName || item.planCode || '-' }}</strong>
                    <small>{{ item.subscriptionStatus || '-' }} / {{ item.quotaPeriod || '-' }}</small>
                  </td>
                  <td>
                    <strong>{{ formatQuota(item.tokenUsed) }} / {{ formatQuota(item.tokenLimit) }}</strong>
                    <div class="admin-quota-bar" :class="{ 'admin-quota-bar--danger': isOverLimit(item.overLimit) }">
                      <span :style="{ width: quotaPercent(item) + '%' }"></span>
                    </div>
                    <small>剩余 {{ formatQuota(item.tokenRemaining) }}</small>
                  </td>
                  <td>{{ formatDateTime(item.createdAt) }}</td>
                  <td>{{ formatDateTime(item.lastActiveAt) }}</td>
                  <td>
                    <button class="admin-link-btn" @click.stop="goDetail(item.id)">查看详情</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="admin-pagination">
            <span>共 {{ total }} 条</span>
            <div class="admin-pagination__actions">
              <button class="admin-btn admin-btn--secondary" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</button>
              <span>第 {{ page }} 页</span>
              <button class="admin-btn admin-btn--secondary" :disabled="page * size >= total || loading" @click="goPage(page + 1)">下一页</button>
            </div>
          </div>
        </div>
      </div>

      <aside v-if="drawerOpen" class="admin-user-drawer" aria-label="用户摘要抽屉">
        <div class="admin-user-drawer__header">
          <div class="admin-user-drawer__identity">
            <span class="admin-avatar admin-avatar--lg">{{ initials(overview?.account.nickname, overview?.account.id) }}</span>
            <div>
              <div class="admin-user-drawer__title">{{ overview?.account.nickname || '用户摘要' }}</div>
              <div class="admin-user-drawer__sub">{{ overview?.account.email || overview?.account.phoneMasked || '-' }}</div>
            </div>
          </div>
          <button class="admin-icon-btn" aria-label="关闭用户摘要抽屉" @click="closeDrawer">×</button>
        </div>

        <div v-if="drawerLoading" class="admin-drawer-state">正在加载用户摘要...</div>
        <div v-else-if="drawerError" class="admin-drawer-state admin-drawer-state--error">{{ drawerError }}</div>
        <template v-else-if="overview">
          <div class="admin-user-drawer__tags">
            <span class="admin-tag" :class="statusTagClass(overview.account.status)">{{ overview.account.status }}</span>
            <span class="admin-tag admin-tag--info">{{ overview.account.studyStage || '-' }}</span>
            <span v-for="role in overview.account.adminRoles" :key="role" class="admin-tag admin-tag--role">{{ role }}</span>
          </div>

          <section class="admin-drawer-card">
            <div class="admin-drawer-card__title">订阅与额度</div>
            <div class="admin-kv"><span>当前套餐</span><strong>{{ overview.subscription?.planName || overview.subscription?.planCode || '-' }}</strong></div>
            <div class="admin-kv"><span>额度使用</span><strong>{{ formatQuota(overview.subscription?.tokenUsed) }} / {{ formatQuota(overview.subscription?.tokenLimit) }}</strong></div>
            <div class="admin-quota-bar" :class="{ 'admin-quota-bar--danger': isOverLimit(overview.subscription?.overLimit) }">
              <span :style="{ width: quotaPercent(overview.subscription) + '%' }"></span>
            </div>
            <div class="admin-drawer-muted">剩余 {{ formatQuota(overview.subscription?.tokenRemaining) }}</div>
          </section>

          <section class="admin-drawer-card">
            <div class="admin-drawer-card__title">
              <span>最近作文</span>
              <button class="admin-link-btn" @click="goPath(overview.quickLinks.essays)">查看更多</button>
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
            <div v-for="log in overview.audit.recentLogs.slice(0, 2)" :key="String(log.id ?? log.createdAt)" class="admin-drawer-row admin-drawer-row--stacked">
              <span>{{ log.createdAt || '-' }}</span>
              <strong>{{ log.action || '-' }}</strong>
            </div>
          </section>

          <div class="admin-user-drawer__actions">
            <button class="admin-btn" @click="goPath(overview.quickLinks.detail)">完整详情</button>
            <button class="admin-btn admin-btn--secondary" @click="goPath(overview.quickLinks.essays)">作文</button>
            <button class="admin-btn admin-btn--secondary" @click="goPath(overview.quickLinks.subscriptions)">订阅</button>
            <button class="admin-btn admin-btn--secondary" @click="goPath(overview.quickLinks.aiUsage)">AI</button>
          </div>
        </template>
      </aside>
    </div>
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
const size = 10
const filters = ref(emptyFilters())
const loading = ref(false)
const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerError = ref('')
const selectedUserId = ref<number | null>(null)
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
  loading.value = true
  try {
    const res = await adminApi.listUsers({ ...buildParams(), page: page.value, size })
    rows.value = res.items
    total.value = res.total
  } catch {
    showToast('加载用户列表失败', 'error')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function resetFilters() {
  filters.value = emptyFilters()
  search()
}

function goPage(nextPage: number) {
  page.value = nextPage
  void load()
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
  void router.push(`/admin/users/${id}`)
}

async function openOverviewDrawer(id: number) {
  selectedUserId.value = id
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
  selectedUserId.value = null
  overview.value = null
  drawerError.value = ''
}

function goPath(path: string) {
  void router.push(path)
}

function formatQuota(value: number | string | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function isOverLimit(value: boolean | number | string | null | undefined) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

function quotaPercent(subscription: AdminUserSubscriptionSnapshot | AdminUserListItem | null) {
  const used = Number(subscription?.tokenUsed ?? 0)
  const limit = Number(subscription?.tokenLimit ?? 0)
  if (limit <= 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

function initials(name: string | null | undefined, id?: number | null) {
  const source = name?.trim() || String(id ?? '?')
  return source.slice(0, 2).toUpperCase()
}

function statusTagClass(status: string | null | undefined) {
  if (status === 'active') return 'admin-tag--success'
  if (status === 'disabled') return 'admin-tag--danger'
  return 'admin-tag--info'
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<style scoped>
.admin-users-center {
  gap: 0;
}

.admin-users-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
  gap: 16px;
  align-items: start;
}

.admin-users-main {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.admin-page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.admin-page-heading h1 {
  margin: 0;
  color: var(--admin-text);
  font-size: 28px;
  line-height: 1.1;
}

.admin-page-heading p {
  margin-top: 6px;
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-page-heading__actions {
  display: flex;
  gap: 10px;
}

.admin-users-filter-card {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 16px;
  padding: 18px;
  border-radius: 12px;
}

.admin-filter-field {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.admin-filter-field span {
  color: var(--admin-text);
  font-size: 12px;
  font-weight: 700;
}

.admin-filter-field--wide {
  grid-column: span 2;
}

.admin-filter-field--range {
  grid-column: span 2;
}

.admin-date-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.admin-date-range > span {
  color: var(--admin-muted);
  font-weight: 500;
}

.admin-users-table-card {
  padding: 0;
  overflow: hidden;
  border-radius: 12px;
}

.admin-users-state {
  padding: 48px;
  color: var(--admin-muted);
  text-align: center;
}

.admin-users-table th {
  font-size: 12px;
  text-transform: none;
  letter-spacing: 0;
  background: rgba(240, 245, 238, 0.7);
}

.admin-users-table td {
  font-size: 13px;
}

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

.admin-user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #dbeafe;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
}

.admin-avatar--lg {
  width: 48px;
  height: 48px;
  font-size: 16px;
}

.admin-row-link--selected {
  background: #eef6ff;
}

.admin-tag {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 3px 8px;
  border-radius: 5px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 11px;
  font-weight: 800;
}

.admin-tag--success {
  background: #dcfce7;
  color: #15803d;
}

.admin-tag--danger {
  background: #fee2e2;
  color: #b91c1c;
}

.admin-tag--info {
  background: #e0f2fe;
  color: #0369a1;
}

.admin-tag--role {
  background: #ede9fe;
  color: #5b21b6;
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

.admin-quota-bar {
  height: 6px;
  overflow: hidden;
  margin-top: 8px;
  border-radius: 999px;
  background: #e5e7eb;
}

.admin-quota-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2563eb;
}

.admin-quota-bar--danger span {
  background: #dc2626;
}

.admin-pagination {
  padding: 14px 18px;
}

.admin-pagination__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-user-drawer {
  position: sticky;
  top: 24px;
  max-height: calc(100vh - 48px);
  overflow: auto;
  padding: 18px;
  border: 1px solid rgba(165, 184, 159, 0.48);
  border-radius: 12px;
  background: rgba(255, 253, 247, 0.97);
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

.admin-user-drawer__identity {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.admin-user-drawer__title {
  font-size: 18px;
  font-weight: 800;
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

.admin-drawer-card {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid rgba(165, 184, 159, 0.48);
  border-radius: 10px;
  background: #fff;
}

.admin-drawer-card__title {
  margin-bottom: 10px;
  font-weight: 800;
}

.admin-drawer-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-top: 1px solid rgba(165, 184, 159, 0.32);
  font-size: 13px;
}

.admin-drawer-row--stacked {
  display: grid;
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
  flex-wrap: wrap;
  margin-top: 14px;
}

@media (max-width: 1280px) {
  .admin-users-shell {
    grid-template-columns: 1fr;
  }

  .admin-user-drawer {
    position: static;
    max-height: none;
  }
}

@media (max-width: 980px) {
  .admin-users-filter-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-filter-field--wide,
  .admin-filter-field--range {
    grid-column: span 2;
  }
}
</style>
