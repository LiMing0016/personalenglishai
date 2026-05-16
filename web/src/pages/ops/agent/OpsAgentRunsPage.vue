<template>
  <section class="admin-section ops-agent-page">
    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Agent 请求记录</div>
        <p class="admin-subtle">查看真实 Agent run、路由决策、模型、usage、状态和 trace。</p>
      </div>
      <span class="admin-badge">Agent Runs</span>
    </div>

    <div class="admin-card">
      <form class="admin-toolbar" @submit.prevent="loadRuns(1)">
        <div class="admin-toolbar-left">
          <input v-model="filters.conversationId" class="admin-input" placeholder="conversation id" />
          <input v-model="filters.userId" class="admin-input" placeholder="user id" />
          <input v-model="filters.intent" class="admin-input" placeholder="intent" />
          <input v-model="filters.targetAgent" class="admin-input" placeholder="target agent" />
          <input v-model="filters.model" class="admin-input" placeholder="model" />
          <select v-model="filters.status" class="admin-select">
            <option value="">全部状态</option>
            <option value="completed">completed</option>
            <option value="failed">failed</option>
            <option value="partial">partial</option>
          </select>
        </div>
        <button class="admin-btn" :disabled="loading">{{ loading ? '查询中' : '查询' }}</button>
      </form>

      <div v-if="loading" class="admin-loading">正在加载 Agent runs...</div>
      <div v-else-if="error" class="admin-error">{{ error }}</div>
      <div v-else-if="runs.length === 0" class="admin-empty">还没有真实 Agent run。发送一条学习助手消息后再刷新这里。</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table ops-agent-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>用户输入</th>
              <th>Intent</th>
              <th>Workflow / Agent</th>
              <th>模型</th>
              <th>Tokens</th>
              <th>Latency</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="run in runs"
              :key="run.runId"
              class="admin-row-link"
              @click="openRun(run.runId)"
            >
              <td>{{ run.createdAt || '-' }}</td>
              <td>
                <div class="ops-agent-message">{{ summarizeText(run.rawUserMessage, 90) }}</div>
                <button class="ops-agent-copy" type="button" @click.stop="copyText(run.runId)">复制 run id</button>
                <button v-if="run.traceId" class="ops-agent-copy" type="button" @click.stop="copyText(run.traceId)">复制 trace id</button>
              </td>
              <td>{{ run.intent || '-' }}</td>
              <td>{{ run.workflow || run.targetAgent || run.agentName || '-' }}</td>
              <td>{{ run.model || '-' }}</td>
              <td>{{ formatTokens(run.totalTokens) }}</td>
              <td>{{ run.latencyMs ?? '-' }} ms</td>
              <td><span class="admin-badge" :class="`ops-agent-status--${run.status || 'unknown'}`">{{ statusLabel(run.status) }}</span></td>
            </tr>
          </tbody>
        </table>
        <div class="admin-pagination">
          <span>共 {{ total }} 条，第 {{ page }} 页</span>
          <div class="admin-toolbar-right">
            <button class="admin-btn admin-btn--secondary" :disabled="page <= 1 || loading" @click="loadRuns(page - 1)">上一页</button>
            <button class="admin-btn admin-btn--secondary" :disabled="page * size >= total || loading" @click="loadRuns(page + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { opsAgentApi, type AgentDebugRun } from '@/api/opsAgent'
import { formatTokens, statusLabel, summarizeText } from './opsAgentView'

const router = useRouter()
const runs = ref<AgentDebugRun[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')
const filters = reactive({
  status: '',
  intent: '',
  targetAgent: '',
  model: '',
  userId: '',
  conversationId: '',
})

async function loadRuns(nextPage = page.value) {
  loading.value = true
  error.value = ''
  try {
    const data = await opsAgentApi.listRuns({
      ...filters,
      page: nextPage,
      size: size.value,
    })
    runs.value = data.items ?? []
    total.value = data.total ?? 0
    page.value = data.page ?? nextPage
    size.value = data.size ?? size.value
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Agent runs 加载失败'
  } finally {
    loading.value = false
  }
}

function openRun(runId: string) {
  void router.push(`/ops/agent/runs/${encodeURIComponent(runId)}`)
}

async function copyText(value: string) {
  await navigator.clipboard?.writeText(value)
}

onMounted(() => {
  void loadRuns(1)
})
</script>
