<template>
  <section class="admin-section ops-agent-page">
    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Prompt 调试</div>
        <p class="admin-subtle">查看每次调用实际渲染后的 system / developer / user prompt、prompt key、版本和 hash。</p>
      </div>
      <span class="admin-badge">Prompt Snapshots</span>
    </div>

    <div class="admin-card">
      <form class="admin-toolbar" @submit.prevent="loadPrompts(1)">
        <div class="admin-toolbar-left">
          <input v-model="filters.promptKey" class="admin-input" placeholder="prompt key" />
          <input v-model="filters.promptHash" class="admin-input" placeholder="prompt hash" />
          <input v-model="filters.agentName" class="admin-input" placeholder="agent" />
          <input v-model="filters.model" class="admin-input" placeholder="model" />
        </div>
        <button class="admin-btn" :disabled="loading">{{ loading ? '查询中' : '查询' }}</button>
      </form>

      <div v-if="loading" class="admin-loading">正在加载 prompt snapshots...</div>
      <div v-else-if="error" class="admin-error">{{ error }}</div>
      <div v-else-if="prompts.length === 0" class="admin-empty">还没有 prompt snapshot。真实 run 记录接入后会显示在这里。</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>Prompt</th>
              <th>Agent</th>
              <th>模型</th>
              <th>Run</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="prompt in prompts" :key="prompt.id ?? `${prompt.runId}-${prompt.promptHash}`">
              <td>{{ prompt.createdAt || '-' }}</td>
              <td>
                <strong>{{ prompt.promptKey || '-' }}</strong>
                <div class="admin-subtle">{{ prompt.promptHash || '-' }}</div>
              </td>
              <td>{{ prompt.agentName || '-' }}</td>
              <td>{{ prompt.model || '-' }}</td>
              <td>
                <router-link v-if="prompt.runId" class="admin-back-link" :to="`/ops/agent/runs/${encodeURIComponent(prompt.runId)}`">打开 Run</router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import { opsAgentApi, type AgentPromptSnapshot } from '@/api/opsAgent'

const prompts = ref<AgentPromptSnapshot[]>([])
const loading = ref(false)
const error = ref('')
const filters = reactive({
  promptKey: '',
  promptHash: '',
  agentName: '',
  model: '',
})

async function loadPrompts(nextPage = 1) {
  loading.value = true
  error.value = ''
  try {
    const data = await opsAgentApi.listPrompts({ ...filters, page: nextPage, size: 20 })
    prompts.value = data.items ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Prompt snapshots 加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPrompts()
})
</script>
