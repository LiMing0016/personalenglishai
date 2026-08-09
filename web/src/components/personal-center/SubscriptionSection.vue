<template>
  <div class="subscription-section">
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
        :class="{ active: plan.planCode === props.status?.planCode }"
      >
        <span class="plan-title">{{ plan.name }}</span>
        <span class="plan-limit">{{ formatTokens(plan.monthlyTokenLimit) }} / 月</span>
        <span class="plan-action">
          {{ plan.planCode === props.status?.planCode ? '当前档位' : '使用兑换码开通' }}
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

const props = defineProps<{
  status: SubscriptionStatus | null
}>()
const emit = defineEmits<{
  statusUpdated: [status: SubscriptionStatus]
}>()

const plans = ref<SubscriptionPlan[]>([])
const redeemCode = ref('')
const redeeming = ref(false)

type PaidPlanCode = 'basic' | 'pro' | 'premium'

const paidPlans = computed(() =>
  plans.value.filter((plan): plan is SubscriptionPlan & { planCode: PaidPlanCode } => plan.planCode !== 'free')
)

function formatTokens(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value % 1_000_000 === 0 ? 0 : 1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(value % 1_000 === 0 ? 0 : 1)}K`
  return String(value)
}

async function loadPlans() {
  const plansRes = await userApi.getSubscriptionPlans()
  plans.value = plansRes.data ?? []
}

async function redeem() {
  const code = redeemCode.value.trim()
  if (!code) return
  redeeming.value = true
  try {
    const res = await userApi.redeemSubscriptionCode(code)
    if (res.data) emit('statusUpdated', res.data)
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
    await loadPlans()
  } catch {
    showToast('加载会员信息失败', 'error')
  }
})
</script>

<style scoped>
.subscription-section {
  max-width: 860px;
}

.redeem-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
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
