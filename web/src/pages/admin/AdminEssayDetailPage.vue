<template>
  <section class="admin-section" v-if="detail">
    <div class="admin-card">
      <h2 class="admin-card-title">作文详情</h2>
      <div class="admin-kv"><span>评测ID</span><strong>{{ detail.evaluationId }}</strong></div>
      <div class="admin-kv"><span>用户</span><strong>{{ detail.user.nickname }} / {{ detail.user.email || '-' }}</strong></div>
      <div class="admin-kv"><span>模式</span><strong>{{ detail.mode }}</strong></div>
      <div class="admin-kv"><span>请求ID</span><strong>{{ detail.requestId || '-' }}</strong></div>
      <div class="admin-kv"><span>时间</span><strong>{{ detail.createdAt }}</strong></div>
    </div>
    <div class="admin-card">
      <h2 class="admin-card-title">题目</h2>
      <pre class="admin-pre">{{ detail.taskPrompt || '-' }}</pre>
    </div>
    <div class="admin-card">
      <h2 class="admin-card-title">作文全文</h2>
      <pre class="admin-pre admin-pre--essay">{{ detail.essayText }}</pre>
    </div>
    <div class="admin-grid-two">
      <div class="admin-card">
        <h2 class="admin-card-title">评分结果</h2>
        <pre class="admin-pre">{{ JSON.stringify(detail.result, null, 2) }}</pre>
      </div>
      <div class="admin-card">
        <h2 class="admin-card-title">任务详情</h2>
        <button class="admin-btn admin-btn--secondary" @click="loadTask">刷新任务</button>
        <pre class="admin-pre">{{ JSON.stringify(task, null, 2) }}</pre>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi, type AdminEssayDetail } from '@/api/admin'
import { showToast } from '@/utils/toast'

const route = useRoute()
const detail = ref<AdminEssayDetail | null>(null)
const task = ref<any>(null)

async function load() {
  try {
    detail.value = await adminApi.getEssayDetail(Number(route.params.id))
  } catch {
    showToast('加载作文详情失败', 'error')
  }
}

async function loadTask() {
  try {
    task.value = await adminApi.getEssayTask(Number(route.params.id))
  } catch {
    task.value = { message: '暂无任务或任务不存在' }
  }
}

onMounted(async () => {
  await load()
  await loadTask()
})
</script>
