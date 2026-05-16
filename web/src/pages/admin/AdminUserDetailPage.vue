<template>
  <section class="admin-section" v-if="detail">
    <div class="admin-grid-two">
      <div class="admin-card">
        <h2 class="admin-card-title">用户信息</h2>
        <div class="admin-kv"><span>ID</span><strong>{{ detail.id }}</strong></div>
        <div class="admin-kv"><span>昵称</span><strong>{{ detail.nickname }}</strong></div>
        <div class="admin-kv"><span>邮箱</span><strong>{{ detail.email || '-' }}</strong></div>
        <div class="admin-kv"><span>手机号</span><strong>{{ detail.phone || '-' }}</strong></div>
        <div class="admin-kv"><span>学段</span><strong>{{ detail.studyStage || '-' }}</strong></div>
        <div class="admin-kv"><span>注册来源</span><strong>{{ detail.registerSource || '-' }}</strong></div>
        <div class="admin-kv"><span>创建时间</span><strong>{{ detail.createdAt || '-' }}</strong></div>
        <div class="admin-kv"><span>最近活跃</span><strong>{{ detail.lastActiveAt || '-' }}</strong></div>
      </div>
      <div class="admin-card">
        <h2 class="admin-card-title">治理操作</h2>
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
      </div>
    </div>
    <div class="admin-card">
      <h2 class="admin-card-title">订阅与额度</h2>
      <div class="admin-grid-three">
        <div class="admin-kv"><span>当前套餐</span><strong>{{ detail.subscription?.planName || detail.subscription?.planCode || '-' }}</strong></div>
        <div class="admin-kv"><span>订阅状态</span><strong>{{ detail.subscription?.subscriptionStatus || '-' }}</strong></div>
        <div class="admin-kv"><span>额度周期</span><strong>{{ detail.subscription?.quotaPeriod || '-' }}</strong></div>
        <div class="admin-kv"><span>已用 / 上限</span><strong>{{ formatQuota(detail.subscription?.tokenUsed) }} / {{ formatQuota(detail.subscription?.tokenLimit) }}</strong></div>
        <div class="admin-kv"><span>剩余额度</span><strong>{{ formatQuota(detail.subscription?.tokenRemaining) }}</strong></div>
        <div class="admin-kv"><span>是否超额</span><strong>{{ isOverLimit(detail.subscription?.overLimit) ? '是' : '否' }}</strong></div>
        <div class="admin-kv"><span>周期开始</span><strong>{{ detail.subscription?.currentPeriodStart || '-' }}</strong></div>
        <div class="admin-kv"><span>周期结束</span><strong>{{ detail.subscription?.currentPeriodEnd || '-' }}</strong></div>
        <div class="admin-kv"><span>用量口径</span><strong>{{ detail.subscription?.usageDate || detail.subscription?.usageMonth || '-' }}</strong></div>
      </div>
    </div>
    <div class="admin-grid-two">
      <div class="admin-card">
        <h2 class="admin-card-title">能力画像</h2>
        <pre class="admin-pre">{{ JSON.stringify(detail.ability, null, 2) }}</pre>
      </div>
      <div class="admin-card">
        <h2 class="admin-card-title">统计摘要</h2>
        <pre class="admin-pre">{{ JSON.stringify(detail.stats, null, 2) }}</pre>
      </div>
    </div>
    <div class="admin-card">
      <h2 class="admin-card-title">最近评测</h2>
      <pre class="admin-pre">{{ JSON.stringify(detail.recentEvaluations, null, 2) }}</pre>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi, type AdminUserDetail } from '@/api/admin'
import { showToast } from '@/utils/toast'

const route = useRoute()
const detail = ref<AdminUserDetail | null>(null)
const status = ref<'active' | 'disabled'>('active')
const roles = ref<string[]>([])

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

function formatQuota(value: number | string | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function isOverLimit(value: boolean | number | string | null | undefined) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

onMounted(load)
</script>
