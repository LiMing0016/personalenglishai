<template>
  <div class="subscription-section">
    <h2 class="section-title">会员订阅</h2>

    <div class="status-panel">
      <div>
        <div class="eyebrow">当前档位</div>
        <div class="plan-name">{{ status?.planName ?? '--' }}</div>
        <div class="period">{{ periodText }}</div>
      </div>
      <div class="quota-summary">
        <span>{{ formatTokens(status?.tokenUsed ?? 0) }}</span>
        <small>/ {{ formatTokens(status?.monthlyTokenLimit ?? 0) }}</small>
      </div>
    </div>

    <div class="usage-panel">
      <div class="usage-row">
        <span>{{ status?.usageMonth ?? '--' }} token 用量</span>
        <strong>{{ usagePercent }}%</strong>
      </div>
      <div class="progress-track">
        <div class="progress-fill" :class="{ danger: status?.overLimit }" :style="{ width: `${Math.min(usagePercent, 100)}%` }"></div>
      </div>
      <div class="usage-meta">
        <span>已用 {{ formatTokens(status?.tokenUsed ?? 0) }}</span>
        <span>剩余 {{ formatTokens(status?.tokenRemaining ?? 0) }}</span>
      </div>
    </div>

    <div class="plans-grid">
      <button
        v-for="plan in paidPlans"
        :key="plan.planCode"
        class="plan-card"
        :class="{ active: plan.planCode === status?.planCode }"
        :disabled="purchasing === plan.planCode"
        @click="purchase(plan.planCode)"
      >
        <span class="plan-title">{{ plan.name }}</span>
        <span class="plan-limit">{{ formatTokens(plan.monthlyTokenLimit) }} / 月</span>
        <span class="plan-action">
          {{ plan.planCode === status?.planCode ? '续购 30 天' : purchasing === plan.planCode ? '开通中...' : '模拟开通' }}
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { userApi, type SubscriptionPlan, type SubscriptionStatus } from '@/api/user'
import { showToast } from '@/utils/toast'

const plans = ref<SubscriptionPlan[]>([])
const status = ref<SubscriptionStatus | null>(null)
const purchasing = ref<string | null>(null)

type PaidPlanCode = 'basic' | 'pro' | 'premium'

const paidPlans = computed(() =>
  plans.value.filter((plan): plan is SubscriptionPlan & { planCode: PaidPlanCode } => plan.planCode !== 'free')
)
const usagePercent = computed(() => {
  const limit = status.value?.monthlyTokenLimit ?? 0
  if (limit <= 0) return 0
  return Math.round(((status.value?.tokenUsed ?? 0) / limit) * 100)
})
const periodText = computed(() => {
  if (!status.value?.currentPeriodEnd) return 'Free 档长期有效'
  return `有效期至 ${formatDate(status.value.currentPeriodEnd)}`
})

function formatTokens(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value % 1_000_000 === 0 ? 0 : 1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(value % 1_000 === 0 ? 0 : 1)}K`
  return String(value)
}

function formatDate(value: string): string {
  const date = new Date(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

async function loadSubscription() {
  const [plansRes, statusRes] = await Promise.all([
    userApi.getSubscriptionPlans(),
    userApi.getMySubscription(),
  ])
  plans.value = plansRes.data ?? []
  status.value = statusRes.data ?? null
}

async function purchase(planCode: PaidPlanCode) {
  purchasing.value = planCode
  try {
    const res = await userApi.mockPurchaseSubscription(planCode)
    status.value = res.data ?? status.value
    showToast('会员已开通', 'success')
  } catch {
    showToast('开通失败，请稍后重试', 'error')
  } finally {
    purchasing.value = null
  }
}

onMounted(async () => {
  try {
    await loadSubscription()
  } catch {
    showToast('加载会员信息失败', 'error')
  }
})
</script>

<style scoped>
.subscription-section {
  max-width: 860px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 24px;
}

.status-panel,
.usage-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.status-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.plan-name {
  font-size: 28px;
  font-weight: 700;
  color: #047857;
}

.period {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.quota-summary {
  text-align: right;
  color: #0f172a;
}

.quota-summary span {
  font-size: 28px;
  font-weight: 700;
}

.quota-summary small {
  color: #64748b;
}

.usage-row,
.usage-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  color: #334155;
}

.progress-track {
  height: 10px;
  background: #e2e8f0;
  border-radius: 999px;
  overflow: hidden;
  margin: 12px 0 10px;
}

.progress-fill {
  height: 100%;
  background: #047857;
  transition: width 0.2s ease;
}

.progress-fill.danger {
  background: #dc2626;
}

.usage-meta {
  color: #64748b;
}

.plans-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.plan-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.plan-card:hover,
.plan-card.active {
  border-color: #047857;
  box-shadow: 0 2px 10px rgba(4, 120, 87, 0.12);
}

.plan-card:disabled {
  opacity: 0.7;
  cursor: progress;
}

.plan-title {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
}

.plan-limit {
  font-size: 13px;
  color: #64748b;
}

.plan-action {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #047857;
}

@media (max-width: 768px) {
  .status-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .quota-summary {
    text-align: left;
  }

  .plans-grid {
    grid-template-columns: 1fr;
  }
}
</style>
