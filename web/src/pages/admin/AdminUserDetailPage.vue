<template>
  <section v-if="detail" class="admin-section admin-user-detail-page">
    <div class="admin-detail-heading">
      <div>
        <button class="admin-link-btn admin-back-inline" @click="router.push('/admin/users')">← 用户中心 / 用户详情</button>
        <h1>用户 360 详情</h1>
      </div>
      <div class="admin-detail-heading__actions">
        <button class="admin-btn admin-btn--secondary" @click="copyRawData">复制原始数据</button>
        <button class="admin-btn admin-btn--secondary" @click="router.push(`/admin/audit-logs?targetUserId=${detail.id}`)">审计日志</button>
      </div>
    </div>

    <div class="admin-card admin-user-profile-card">
      <div class="admin-user-profile-card__identity">
        <span class="admin-avatar admin-avatar--xl">{{ initials(detail.nickname, detail.id) }}</span>
        <div>
          <h2>{{ detail.nickname || '-' }}</h2>
          <p>{{ detail.email || detail.phone || '-' }}</p>
          <div class="admin-user-tags">
            <span class="admin-tag" :class="statusTagClass(detail.status)">{{ detail.status }}</span>
            <span class="admin-tag admin-tag--info">{{ detail.studyStage || '-' }}</span>
            <span v-for="role in detail.adminRoles" :key="role" class="admin-tag admin-tag--role">{{ role }}</span>
          </div>
        </div>
      </div>
      <div class="admin-user-metrics">
        <article v-for="metric in profileMetrics" :key="metric.label" class="admin-user-metric">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </div>
    </div>

    <nav class="admin-detail-tabs" aria-label="用户详情模块">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div v-if="activeTab === 'overview'" class="admin-detail-grid">
      <section class="admin-card admin-detail-panel">
        <div class="admin-panel-head">
          <h2>1. 账号资料</h2>
          <button class="admin-link-btn" @click="activeTab = 'account'">编辑</button>
        </div>
        <div class="admin-kv"><span>ID</span><strong>{{ detail.id }}</strong></div>
        <div class="admin-kv"><span>邮箱</span><strong>{{ detail.email || '-' }}</strong></div>
        <div class="admin-kv"><span>手机号</span><strong>{{ maskPhone(detail.phone) }}</strong></div>
        <div class="admin-kv"><span>注册来源</span><strong>{{ detail.registerSource || '-' }}</strong></div>
        <div class="admin-kv"><span>创建时间</span><strong>{{ formatDateTime(detail.createdAt) }}</strong></div>
        <div class="admin-kv"><span>最近活跃</span><strong>{{ formatDateTime(detail.lastActiveAt) }}</strong></div>
      </section>

      <section class="admin-card admin-detail-panel admin-detail-panel--wide">
        <div class="admin-panel-head">
          <h2>2. 学习画像</h2>
          <button class="admin-link-btn" @click="activeTab = 'learning'">查看画像详情</button>
        </div>
        <div class="admin-learning-panel">
          <div>
            <div class="admin-kv"><span>学段</span><strong>{{ detail.studyStage || '-' }}</strong></div>
            <div class="admin-kv"><span>AI 模式</span><strong>{{ detail.aiMode ?? '-' }}</strong></div>
            <div class="admin-kv"><span>样本数</span><strong>{{ abilityValue('sampleCount') }}</strong></div>
            <div class="admin-kv"><span>画像置信度</span><strong>{{ abilityValue('confidence') }}</strong></div>
            <div class="admin-kv"><span>最近更新时间</span><strong>{{ formatDateTime(abilityValue('updatedAt')) }}</strong></div>
          </div>
          <div class="admin-radar-placeholder" aria-label="能力雷达图">
            <span>雷达</span>
          </div>
        </div>
        <p class="admin-panel-note">写作连贯性、词汇多样性和结构稳定性可作为后续运营干预重点。</p>
      </section>

      <section class="admin-card admin-detail-panel">
        <div class="admin-panel-head">
          <h2>3. 订阅与额度</h2>
          <button class="admin-link-btn" @click="activeTab = 'subscription'">查看订阅详情</button>
        </div>
        <div class="admin-kv"><span>当前套餐</span><strong>{{ detail.subscription?.planName || detail.subscription?.planCode || '-' }}</strong></div>
        <div class="admin-kv"><span>订阅状态</span><strong>{{ detail.subscription?.subscriptionStatus || '-' }}</strong></div>
        <div class="admin-kv"><span>当前周期</span><strong>{{ formatDateTime(detail.subscription?.currentPeriodStart) }} ~ {{ formatDateTime(detail.subscription?.currentPeriodEnd) }}</strong></div>
        <div class="admin-kv"><span>额度使用</span><strong>{{ formatQuota(detail.subscription?.tokenUsed) }} / {{ formatQuota(detail.subscription?.tokenLimit) }}</strong></div>
        <div class="admin-quota-bar" :class="{ 'admin-quota-bar--danger': isOverLimit(detail.subscription?.overLimit) }">
          <span :style="{ width: quotaPercent(detail.subscription) + '%' }"></span>
        </div>
        <div class="admin-kv"><span>是否超额</span><strong>{{ isOverLimit(detail.subscription?.overLimit) ? '是' : '否' }}</strong></div>
      </section>

      <section class="admin-card admin-detail-panel admin-detail-panel--half">
        <div class="admin-panel-head">
          <h2>4. 作文与评测</h2>
          <button class="admin-link-btn" @click="activeTab = 'writing'">查看该用户全部作文</button>
        </div>
        <table class="admin-mini-table">
          <thead><tr><th>评测ID</th><th>模式</th><th>分数</th><th>创建时间</th></tr></thead>
          <tbody>
            <tr v-for="item in detail.recentEvaluations.slice(0, 4)" :key="item.id">
              <td>#{{ item.id }}</td>
              <td>{{ item.mode }}</td>
              <td>{{ item.gaokaoScore ?? item.overallScore ?? '-' }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
            <tr v-if="detail.recentEvaluations.length === 0"><td colspan="4">暂无评测记录</td></tr>
          </tbody>
        </table>
      </section>

      <section class="admin-card admin-detail-panel admin-detail-panel--half">
        <div class="admin-panel-head">
          <h2>5. AI 使用记录</h2>
          <button class="admin-link-btn" @click="activeTab = 'ai'">查看 AI 月度</button>
        </div>
        <table class="admin-mini-table">
          <thead><tr><th>请求时间</th><th>模型</th><th>tokens</th><th>traceId</th></tr></thead>
          <tbody>
            <tr v-for="item in aiUsageRecords.slice(0, 4)" :key="String(item.id ?? item.traceId ?? item.occurredAt)">
              <td>{{ formatDateTime(item.occurredAt) }}</td>
              <td>{{ item.model || '-' }}</td>
              <td>{{ formatQuota(item.totalTokens) }}</td>
              <td>{{ item.traceId || '-' }}</td>
            </tr>
            <tr v-if="aiUsageRecords.length === 0"><td colspan="4">暂无 AI 用量事件</td></tr>
          </tbody>
        </table>
      </section>

      <section class="admin-card admin-detail-panel admin-detail-panel--full">
        <div class="admin-panel-head">
          <h2>6. 审计日志</h2>
          <button class="admin-link-btn" @click="activeTab = 'audit'">查看审计日志</button>
        </div>
        <table class="admin-mini-table">
          <thead><tr><th>操作时间</th><th>管理员</th><th>action</th><th>resourceType</th><th>resourceId</th></tr></thead>
          <tbody>
            <tr v-for="log in auditLogs.slice(0, 5)" :key="String(log.id ?? log.createdAt)">
              <td>{{ formatDateTime(log.createdAt) }}</td>
              <td>{{ log.adminNickname || log.adminUserId || '-' }}</td>
              <td>{{ log.action || '-' }}</td>
              <td>{{ log.resourceType || '-' }}</td>
              <td>{{ log.resourceId || '-' }}</td>
            </tr>
            <tr v-if="auditLogs.length === 0"><td colspan="5">暂无审计日志</td></tr>
          </tbody>
        </table>
      </section>
    </div>

    <div v-else-if="activeTab === 'account'" class="admin-detail-grid">
      <section class="admin-card admin-detail-panel">
        <h2>账号资料</h2>
        <div class="admin-kv"><span>ID</span><strong>{{ detail.id }}</strong></div>
        <div class="admin-kv"><span>昵称</span><strong>{{ detail.nickname }}</strong></div>
        <div class="admin-kv"><span>邮箱</span><strong>{{ detail.email || '-' }}</strong></div>
        <div class="admin-kv"><span>手机号</span><strong>{{ detail.phone || '-' }}</strong></div>
        <div class="admin-kv"><span>学段</span><strong>{{ detail.studyStage || '-' }}</strong></div>
        <div class="admin-kv"><span>注册来源</span><strong>{{ detail.registerSource || '-' }}</strong></div>
      </section>
      <section class="admin-card admin-detail-panel">
        <h2>治理操作</h2>
        <label class="admin-label">状态</label>
        <select v-model="status" class="admin-select">
          <option value="active">active</option>
          <option value="disabled">disabled</option>
        </select>
        <button class="admin-btn" @click="saveStatus">保存状态</button>
        <label class="admin-label">管理员角色</label>
        <div class="admin-checkbox-group">
          <label><input v-model="roles" type="checkbox" value="super_admin" /> super_admin</label>
          <label><input v-model="roles" type="checkbox" value="support_admin" /> support_admin</label>
          <label><input v-model="roles" type="checkbox" value="content_admin" /> content_admin</label>
        </div>
        <button class="admin-btn admin-btn--secondary" @click="saveRoles">保存角色</button>
      </section>
    </div>

    <div v-else-if="activeTab === 'learning'" class="admin-card admin-detail-panel">
      <h2>学习画像</h2>
      <pre class="admin-pre">{{ JSON.stringify(detail.ability, null, 2) }}</pre>
    </div>

    <div v-else-if="activeTab === 'subscription'" class="admin-card admin-detail-panel">
      <h2>订阅与额度</h2>
      <div class="admin-grid-three">
        <div class="admin-kv"><span>当前套餐</span><strong>{{ detail.subscription?.planName || detail.subscription?.planCode || '-' }}</strong></div>
        <div class="admin-kv"><span>订阅状态</span><strong>{{ detail.subscription?.subscriptionStatus || '-' }}</strong></div>
        <div class="admin-kv"><span>额度周期</span><strong>{{ detail.subscription?.quotaPeriod || '-' }}</strong></div>
        <div class="admin-kv"><span>已用 / 上限</span><strong>{{ formatQuota(detail.subscription?.tokenUsed) }} / {{ formatQuota(detail.subscription?.tokenLimit) }}</strong></div>
        <div class="admin-kv"><span>剩余额度</span><strong>{{ formatQuota(detail.subscription?.tokenRemaining) }}</strong></div>
        <div class="admin-kv"><span>是否超额</span><strong>{{ isOverLimit(detail.subscription?.overLimit) ? '是' : '否' }}</strong></div>
        <div class="admin-kv"><span>周期开始</span><strong>{{ formatDateTime(detail.subscription?.currentPeriodStart) }}</strong></div>
        <div class="admin-kv"><span>周期结束</span><strong>{{ formatDateTime(detail.subscription?.currentPeriodEnd) }}</strong></div>
        <div class="admin-kv"><span>用量口径</span><strong>{{ detail.subscription?.usageDate || detail.subscription?.usageMonth || '-' }}</strong></div>
      </div>
    </div>

    <div v-else-if="activeTab === 'writing'" class="admin-card admin-detail-panel">
      <h2>作文与评测</h2>
      <pre class="admin-pre">{{ JSON.stringify(detail.recentEvaluations, null, 2) }}</pre>
    </div>

    <div v-else-if="activeTab === 'ai'" class="admin-card admin-detail-panel">
      <h2>AI 使用记录</h2>
      <pre class="admin-pre">{{ JSON.stringify(aiUsageRecords, null, 2) }}</pre>
    </div>

    <div v-else-if="activeTab === 'audit'" class="admin-card admin-detail-panel">
      <h2>审计日志</h2>
      <pre class="admin-pre">{{ JSON.stringify(auditLogs, null, 2) }}</pre>
    </div>

    <div v-else-if="activeTab === 'raw'" class="admin-card admin-detail-panel">
      <h2>原始数据</h2>
      <pre class="admin-pre">{{ JSON.stringify(detail, null, 2) }}</pre>
    </div>
  </section>
  <section v-else class="admin-section">
    <div class="admin-card admin-loading">正在加载用户详情...</div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi, type AdminUserDetail, type AdminUserSubscriptionSnapshot } from '@/api/admin'
import { showToast } from '@/utils/toast'

type TabKey = 'overview' | 'account' | 'learning' | 'subscription' | 'writing' | 'ai' | 'audit' | 'raw'

const route = useRoute()
const router = useRouter()
const detail = ref<AdminUserDetail | null>(null)
const status = ref<'active' | 'disabled'>('active')
const roles = ref<string[]>([])
const activeTab = ref<TabKey>('overview')

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'overview', label: '概览' },
  { key: 'account', label: '账号资料' },
  { key: 'learning', label: '学习画像' },
  { key: 'subscription', label: '订阅与额度' },
  { key: 'writing', label: '作文与评测' },
  { key: 'ai', label: 'AI 使用记录' },
  { key: 'audit', label: '审计日志' },
  { key: 'raw', label: '原始数据' },
]

const aiUsageRecords = computed(() => detail.value?.aiUsageRecords ?? [])
const auditLogs = computed(() => detail.value?.auditLogs ?? [])

const profileMetrics = computed(() => [
  { label: '账号状态', value: detail.value?.status || '-', hint: '当前状态' },
  { label: '当前权益', value: detail.value?.subscription?.planName || detail.value?.subscription?.planCode || '-', hint: detail.value?.subscription?.subscriptionStatus || '-' },
  { label: '额度使用', value: `${formatQuota(detail.value?.subscription?.tokenUsed)} / ${formatQuota(detail.value?.subscription?.tokenLimit)}`, hint: `${quotaPercent(detail.value?.subscription)}%` },
  { label: '写作活跃', value: formatQuota(detail.value?.stats?.totalEssays as number | undefined), hint: '累计作文' },
  { label: 'AI 使用', value: formatQuota(totalAiTokens.value), hint: '最近记录 tokens' },
  { label: '治理记录', value: formatQuota(auditLogs.value.length), hint: '最近审计' },
])

const totalAiTokens = computed(() =>
  aiUsageRecords.value.reduce((sum, item) => sum + Number(item.totalTokens ?? 0), 0),
)

async function load() {
  try {
    detail.value = await adminApi.getUserDetail(Number(route.params.id))
    status.value = (detail.value.status as 'active' | 'disabled') || 'active'
    roles.value = [...(detail.value.adminRoles || [])]
  } catch {
    showToast('加载用户详情失败', 'error')
  }
}

async function saveStatus() {
  try {
    await adminApi.updateUserStatus(Number(route.params.id), { status: status.value })
    showToast('用户状态已更新', 'success')
    await load()
  } catch {
    showToast('更新状态失败', 'error')
  }
}

async function saveRoles() {
  try {
    await adminApi.updateUserRoles(Number(route.params.id), { adminRoles: roles.value })
    showToast('管理员角色已更新', 'success')
    await load()
  } catch {
    showToast('更新角色失败', 'error')
  }
}

async function copyRawData() {
  if (!detail.value) return
  await navigator.clipboard?.writeText(JSON.stringify(detail.value, null, 2))
  showToast('原始数据已复制', 'success')
}

function abilityValue(key: string) {
  return detail.value?.ability?.[key] ?? '-'
}

function formatQuota(value: number | string | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function isOverLimit(value: boolean | number | string | null | undefined) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

function quotaPercent(subscription: AdminUserSubscriptionSnapshot | null | undefined) {
  const used = Number(subscription?.tokenUsed ?? 0)
  const limit = Number(subscription?.tokenLimit ?? 0)
  if (limit <= 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

function initials(name: string | null | undefined, id?: number | null) {
  const source = name?.trim() || String(id ?? '?')
  return source.slice(0, 2).toUpperCase()
}

function statusTagClass(value: string | null | undefined) {
  if (value === 'active') return 'admin-tag--success'
  if (value === 'disabled') return 'admin-tag--danger'
  return 'admin-tag--info'
}

function formatDateTime(value: unknown) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function maskPhone(value: string | null | undefined) {
  if (!value || value.length < 7) return value || '-'
  return `${value.slice(0, 3)}****${value.slice(-4)}`
}

onMounted(load)
</script>

<style scoped>
.admin-user-detail-page {
  gap: 16px;
}

.admin-detail-heading,
.admin-user-profile-card,
.admin-user-profile-card__identity,
.admin-detail-heading__actions,
.admin-user-tags,
.admin-panel-head {
  display: flex;
  align-items: center;
}

.admin-detail-heading {
  justify-content: space-between;
  gap: 16px;
}

.admin-detail-heading h1 {
  margin-top: 6px;
  color: var(--admin-text);
  font-size: 28px;
  line-height: 1.1;
}

.admin-back-inline {
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-detail-heading__actions {
  justify-content: flex-end;
  gap: 10px;
}

.admin-user-profile-card {
  justify-content: space-between;
  gap: 24px;
  padding: 18px;
  border-radius: 12px;
}

.admin-user-profile-card__identity {
  gap: 14px;
  min-width: 260px;
}

.admin-user-profile-card h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 20px;
}

.admin-user-profile-card p {
  margin-top: 2px;
  color: var(--admin-muted);
  font-size: 13px;
}

.admin-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #dbeafe;
  color: #2563eb;
  font-weight: 900;
}

.admin-avatar--xl {
  width: 62px;
  height: 62px;
  font-size: 20px;
}

.admin-user-tags {
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.admin-tag {
  display: inline-flex;
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

.admin-user-metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(100px, 1fr));
  gap: 10px;
  flex: 1;
}

.admin-user-metric {
  display: grid;
  gap: 4px;
  min-height: 76px;
  padding: 12px;
  border: 1px solid rgba(165, 184, 159, 0.48);
  border-radius: 10px;
  background: #fff;
  text-align: center;
}

.admin-user-metric span,
.admin-user-metric small {
  color: var(--admin-muted);
  font-size: 11px;
}

.admin-user-metric strong {
  color: var(--admin-text);
  font-size: 16px;
}

.admin-detail-tabs {
  display: flex;
  gap: 18px;
  overflow-x: auto;
  border-bottom: 1px solid rgba(165, 184, 159, 0.5);
}

.admin-detail-tabs button {
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--admin-text);
  padding: 10px 0;
  font-weight: 700;
  white-space: nowrap;
}

.admin-detail-tabs button.active {
  border-color: #2563eb;
  color: #2563eb;
}

.admin-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.admin-detail-panel {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 16px;
  border-radius: 12px;
}

.admin-detail-panel h2,
.admin-panel-head h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 15px;
}

.admin-detail-panel--wide {
  grid-column: span 1;
}

.admin-detail-panel--half {
  grid-column: span 1 / span 1;
}

.admin-detail-panel--full {
  grid-column: 1 / -1;
}

.admin-panel-head {
  justify-content: space-between;
  gap: 12px;
}

.admin-learning-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 16px;
  align-items: center;
}

.admin-radar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 118px;
  height: 118px;
  border-radius: 50%;
  background:
    linear-gradient(45deg, transparent 48%, rgba(37, 99, 235, 0.28) 49%, rgba(37, 99, 235, 0.28) 51%, transparent 52%),
    radial-gradient(circle, #dbeafe 0%, #eff6ff 70%);
  color: #2563eb;
  font-weight: 900;
}

.admin-panel-note {
  color: var(--admin-muted);
  font-size: 12px;
  line-height: 1.6;
}

.admin-mini-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.admin-mini-table th,
.admin-mini-table td {
  padding: 8px 6px;
  border-bottom: 1px solid rgba(165, 184, 159, 0.32);
  text-align: left;
}

.admin-mini-table th {
  color: var(--admin-muted);
  font-weight: 800;
}

.admin-quota-bar {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #e5e7eb;
}

.admin-quota-bar span {
  display: block;
  height: 100%;
  background: #2563eb;
}

.admin-quota-bar--danger span {
  background: #dc2626;
}

.admin-link-btn {
  border: 0;
  background: transparent;
  color: var(--admin-accent);
  cursor: pointer;
  font: inherit;
  padding: 0;
}

.admin-checkbox-group {
  display: grid;
  gap: 8px;
}

@media (max-width: 1280px) {
  .admin-user-profile-card {
    display: grid;
  }

  .admin-user-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .admin-detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
