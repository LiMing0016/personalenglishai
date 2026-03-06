<template>
  <div class="referral-section">
    <!-- Header -->
    <h2 class="section-title">邀请激励计划</h2>
    <p class="section-desc">邀请好友加入，好友订阅后你将获得积分奖励</p>

    <!-- Stats Cards -->
    <div class="stats-grid">
      <div class="stat-card accent">
        <div class="stat-value">{{ stats.points }}</div>
        <div class="stat-label">我的积分</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalInvited }}</div>
        <div class="stat-label">邀请人数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.subscribedCount }}</div>
        <div class="stat-label">已订阅</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.commissionRate }}%</div>
        <div class="stat-label">返佣比例</div>
      </div>
    </div>

    <!-- Invite Code -->
    <div class="invite-box">
      <div class="invite-header">
        <span class="invite-label">我的邀请码</span>
        <button class="refresh-btn" @click="refreshCode" :disabled="refreshing" title="刷新邀请码">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" :class="{ spinning: refreshing }">
            <path d="M14 2v4h-4M2 14v-4h4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2.51 5.87A6 6 0 0113.43 4L14 6M1.57 12A6 6 0 0013.49 10.13" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
      <div class="code-row">
        <span class="invite-code">{{ inviteCode || '--------' }}</span>
        <button class="copy-btn" @click="copyCode" :disabled="!inviteCode">
          {{ copyText }}
        </button>
      </div>
      <div class="invite-link-row" v-if="inviteLink">
        <input class="invite-link-input" :value="inviteLink" readonly @focus="($event.target as HTMLInputElement).select()" />
        <button class="copy-btn small" @click="copyLink">复制链接</button>
      </div>
    </div>

    <!-- Rules -->
    <div class="rules-box">
      <h3 class="rules-title">活动规则</h3>
      <ul class="rules-list">
        <li>分享你的专属邀请码或邀请链接给好友</li>
        <li>好友通过邀请码注册并完成订阅</li>
        <li>好友每次订阅付费，你将获得 <strong>{{ stats.commissionRate }}%</strong> 的积分返佣</li>
        <li>积分可用于兑换会员时长或其他权益</li>
        <li>仅支持一级邀请关系，不支持多级传递</li>
      </ul>
    </div>

    <!-- Invite History -->
    <div class="history-box">
      <h3 class="history-title">邀请记录</h3>
      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="records.length === 0" class="empty-state">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <circle cx="24" cy="24" r="20" stroke="#e2e8f0" stroke-width="2"/>
          <path d="M17 24h14M24 17v14" stroke="#cbd5e1" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p>还没有邀请记录，快去分享邀请码吧</p>
      </div>
      <table v-else class="history-table">
        <thead>
          <tr>
            <th>用户</th>
            <th>注册时间</th>
            <th>状态</th>
            <th>获得积分</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in records" :key="r.id">
            <td>{{ r.nickname }}</td>
            <td>{{ formatDate(r.registeredAt) }}</td>
            <td>
              <span class="status-tag" :class="r.subscribed ? 'subscribed' : 'registered'">
                {{ r.subscribed ? '已订阅' : '已注册' }}
              </span>
            </td>
            <td class="points-cell">{{ r.pointsEarned > 0 ? '+' + r.pointsEarned : '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from '@/utils/toast'

interface ReferralRecord {
  id: number
  nickname: string
  registeredAt: string
  subscribed: boolean
  pointsEarned: number
}

interface ReferralStats {
  points: number
  totalInvited: number
  subscribedCount: number
  commissionRate: number
}

const inviteCode = ref('ENG2026X')
const copyText = ref('复制')
const refreshing = ref(false)
const loading = ref(false)

const stats = ref<ReferralStats>({
  points: 1280,
  totalInvited: 5,
  subscribedCount: 3,
  commissionRate: 10,
})

const records = ref<ReferralRecord[]>([
  { id: 1, nickname: '小明', registeredAt: '2026-02-15', subscribed: true, pointsEarned: 200 },
  { id: 2, nickname: '学英语的猫', registeredAt: '2026-02-20', subscribed: true, pointsEarned: 350 },
  { id: 3, nickname: 'Tom', registeredAt: '2026-03-01', subscribed: true, pointsEarned: 730 },
  { id: 4, nickname: 'Lucy', registeredAt: '2026-03-03', subscribed: false, pointsEarned: 0 },
  { id: 5, nickname: 'English Lover', registeredAt: '2026-03-05', subscribed: false, pointsEarned: 0 },
])

const inviteLink = computed(() => {
  if (!inviteCode.value) return ''
  return `${window.location.origin}/register?ref=${inviteCode.value}`
})

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(inviteCode.value)
    copyText.value = '已复制'
    setTimeout(() => { copyText.value = '复制' }, 1500)
  } catch {
    showToast('复制失败，请手动复制', 'error')
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(inviteLink.value)
    showToast('链接已复制', 'success')
  } catch {
    showToast('复制失败', 'error')
  }
}

async function refreshCode() {
  refreshing.value = true
  // TODO: call API to regenerate invite code
  setTimeout(() => {
    refreshing.value = false
    showToast('邀请码已刷新', 'success')
  }, 600)
}

onMounted(async () => {
  // TODO: fetch real data from API
  // loading.value = true
  // const res = await referralApi.getStats()
  // stats.value = res.data
  // const list = await referralApi.getRecords()
  // records.value = list.data
  // loading.value = false
})
</script>

<style scoped>
.referral-section {
  max-width: 720px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 6px;
}

.section-desc {
  font-size: 14px;
  color: #64748b;
  margin: 0 0 24px;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  text-align: center;
}

.stat-card.accent {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.stat-card.accent .stat-value {
  color: #047857;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

/* Invite Box */
.invite-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 24px;
}

.invite-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.invite-label {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.refresh-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  transition: color 0.15s;
}
.refresh-btn:hover {
  color: #047857;
}
.refresh-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.spinning {
  animation: spin 0.6s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.code-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.invite-code {
  font-size: 28px;
  font-weight: 700;
  color: #047857;
  letter-spacing: 3px;
  font-family: 'Courier New', Courier, monospace;
}

.copy-btn {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.copy-btn:hover {
  background: #065f46;
}
.copy-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}
.copy-btn.small {
  padding: 5px 12px;
  font-size: 12px;
}

.invite-link-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.invite-link-input {
  flex: 1;
  padding: 7px 10px;
  font-size: 13px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  outline: none;
}
.invite-link-input:focus {
  border-color: #047857;
}

/* Rules */
.rules-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 24px;
}

.rules-title {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  margin: 0 0 12px;
}

.rules-list {
  margin: 0;
  padding-left: 20px;
  list-style: decimal;
}

.rules-list li {
  font-size: 13px;
  color: #475569;
  line-height: 2;
}

.rules-list strong {
  color: #047857;
}

/* History */
.history-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 20px;
}

.history-title {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  margin: 0 0 16px;
}

.loading-state {
  text-align: center;
  color: #94a3b8;
  padding: 24px;
  font-size: 14px;
}

.empty-state {
  text-align: center;
  padding: 32px;
  color: #94a3b8;
}
.empty-state p {
  margin: 12px 0 0;
  font-size: 14px;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
}

.history-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  padding: 8px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.history-table td {
  padding: 10px 12px;
  font-size: 14px;
  color: #334155;
  border-bottom: 1px solid #f1f5f9;
}

.history-table tr:last-child td {
  border-bottom: none;
}

.status-tag {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
}
.status-tag.subscribed {
  background: #ecfdf5;
  color: #047857;
}
.status-tag.registered {
  background: #f1f5f9;
  color: #64748b;
}

.points-cell {
  font-weight: 600;
  color: #047857;
}

/* Responsive */
@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .invite-code {
    font-size: 22px;
  }
  .invite-link-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
