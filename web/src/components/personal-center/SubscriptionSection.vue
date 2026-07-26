<template>
  <div class="subscription-section">
    <div class="section-heading">
      <p class="section-eyebrow">订阅与用量</p>
      <h2 class="section-title">当前权益与兑换码</h2>
      <p class="section-description">正式支付上线前，使用兑换码开通或续期；兑换记录将继续保留。</p>
    </div>

    <div class="status-panel">
      <div>
        <div class="eyebrow">当前档位</div>
        <div class="plan-name">{{ status?.planName ?? '--' }}</div>
        <div class="period">{{ periodText }}</div>
      </div>
      <div class="quota-summary">
        <span>{{ formatTokens(status?.tokenUsed ?? 0) }}</span>
        <small>/ {{ formatTokens(effectiveLimit) }}</small>
      </div>
    </div>

    <AiUsageActivityPanel />

    <form class="redeem-panel" @submit.prevent="redeem">
      <label class="redeem-label" for="subscription-code">兑换会员码</label>
      <div class="redeem-row">
        <input
          id="subscription-code"
          v-model="redeemCode"
          class="redeem-input"
          type="text"
          autocomplete="off"
          placeholder="XXXX-XXXX-XXXX-XXXX"
          :disabled="redeeming"
        />
        <button class="redeem-btn" type="submit" :disabled="redeeming || !redeemCode.trim()">
          {{ redeeming ? '兑换中...' : '兑换' }}
        </button>
      </div>
    </form>

    <div class="plans-grid">
      <div
        v-for="plan in paidPlans"
        :key="plan.planCode"
        class="plan-card"
        :class="{ active: plan.planCode === status?.planCode }"
      >
        <span class="plan-title">{{ plan.name }}</span>
        <span class="plan-limit">{{ formatTokens(plan.monthlyTokenLimit) }} / 月</span>
        <span class="plan-action">
          {{ plan.planCode === status?.planCode ? '当前档位' : '使用兑换码开通' }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { userApi, type SubscriptionPlan, type SubscriptionStatus } from '@/api/user'
import { showToast } from '@/utils/toast'
import AiUsageActivityPanel from './AiUsageActivityPanel.vue'

const plans = ref<SubscriptionPlan[]>([])
const status = ref<SubscriptionStatus | null>(null)
const redeemCode = ref('')
const redeeming = ref(false)

type PaidPlanCode = 'basic' | 'pro' | 'premium'

const paidPlans = computed(() =>
  plans.value.filter((plan): plan is SubscriptionPlan & { planCode: PaidPlanCode } => plan.planCode !== 'free')
)
const effectiveLimit = computed(() => status.value?.tokenLimit ?? status.value?.monthlyTokenLimit ?? 0)
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

async function redeem() {
  const code = redeemCode.value.trim()
  if (!code) return
  redeeming.value = true
  try {
    const res = await userApi.redeemSubscriptionCode(code)
    status.value = res.data ?? status.value
    redeemCode.value = ''
    showToast('会员码兑换成功', 'success')
  } catch {
    showToast('会员码无效或不可用', 'error')
  } finally {
    redeeming.value = false
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

.section-heading {
  margin-bottom: 24px;
}

.section-eyebrow {
  margin: 0 0 5px;
  color: #7a8da2;
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.12em;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.section-description {
  margin: 9px 0 0;
  color: #6f8297;
  font-size: 13px;
}

.status-panel,
.redeem-panel {
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

.redeem-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.redeem-row {
  display: flex;
  gap: 10px;
}

.redeem-input {
  flex: 1;
  min-width: 0;
  height: 40px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  color: #0f172a;
}

.redeem-input:focus {
  border-color: #047857;
  outline: none;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.12);
}

.redeem-btn {
  height: 40px;
  border: none;
  border-radius: 8px;
  padding: 0 18px;
  background: #047857;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.redeem-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
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
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.plan-card.active {
  border-color: #047857;
  box-shadow: 0 2px 10px rgba(4, 120, 87, 0.12);
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

  .redeem-row {
    flex-direction: column;
  }

  .redeem-btn {
    width: 100%;
  }
}
</style>
