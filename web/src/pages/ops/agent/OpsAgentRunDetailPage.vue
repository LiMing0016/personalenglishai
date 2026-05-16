<template>
  <section class="admin-section ops-agent-page">
    <router-link to="/ops/agent/runs" class="admin-back-link">返回请求记录</router-link>

    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Agent Run 详情</div>
        <p class="admin-subtle">按排查顺序查看基础信息、RouteRequest、RoutingDecision、Steps、Prompt、Model IO 和 usage。</p>
      </div>
      <button class="admin-btn admin-btn--secondary" :disabled="!run" @click="copyDebugJson">复制完整 JSON</button>
    </div>

    <div v-if="loading" class="admin-loading">正在加载 run...</div>
    <div v-else-if="error" class="admin-error">{{ error }}</div>
    <template v-else-if="run">
      <div class="admin-card">
        <h2 class="admin-card-title">基础信息</h2>
        <div class="admin-grid-three">
          <div class="admin-stat"><div class="admin-stat-label">Run ID</div><div class="admin-stat-value ops-agent-small">{{ run.runId }}</div></div>
          <div class="admin-stat"><div class="admin-stat-label">模型</div><div class="admin-stat-value ops-agent-small">{{ run.model || '-' }}</div></div>
          <div class="admin-stat"><div class="admin-stat-label">状态</div><div class="admin-stat-value ops-agent-small">{{ statusLabel(run.status) }}</div></div>
        </div>
        <div class="admin-kv"><span>Trace ID</span><strong>{{ run.traceId || '-' }}</strong></div>
        <div class="admin-kv"><span>Conversation</span><strong>{{ run.conversationId || '-' }}</strong></div>
        <div class="admin-kv"><span>Target Agent</span><strong>{{ run.targetAgent || run.agentName || '-' }}</strong></div>
        <div class="admin-kv"><span>Tokens</span><strong>{{ formatTokens(run.totalTokens) }}</strong></div>
        <div class="admin-kv"><span>Latency</span><strong>{{ run.latencyMs ?? '-' }} ms</strong></div>
      </div>

      <div class="admin-grid-two">
        <div class="admin-card">
          <h2 class="admin-card-title">RouteRequest</h2>
          <pre class="admin-pre">{{ formatJson(run.routeRequest) }}</pre>
        </div>
        <div class="admin-card">
          <h2 class="admin-card-title">RoutingDecision</h2>
          <pre class="admin-pre">{{ formatJson(run.routingDecision) }}</pre>
        </div>
      </div>

      <div class="admin-card">
        <h2 class="admin-card-title">Steps</h2>
        <div v-if="!run.steps?.length" class="admin-empty">暂无 step 记录。</div>
        <div v-else class="ops-agent-steps">
          <div v-for="step in run.steps" :key="`${step.stepOrder}-${step.stepType}`" class="ops-agent-step">
            <div class="ops-agent-step__head">
              <strong>{{ step.stepOrder }}. {{ step.stepType }}</strong>
              <span>{{ step.agentName || '-' }}</span>
            </div>
            <pre class="admin-pre">{{ formatJson({ input: step.inputJson, output: step.outputJson, usage: step.usageJson, error: step.errorMessage }) }}</pre>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <h2 class="admin-card-title">Prompt Snapshots</h2>
        <div v-if="!run.prompts?.length" class="admin-empty">暂无 prompt snapshot。</div>
        <div v-else class="ops-agent-steps">
          <details v-for="(prompt, index) in run.prompts" :key="prompt.id ?? prompt.promptHash ?? index" class="ops-agent-step">
            <summary>{{ prompt.promptKey || 'prompt' }} / {{ prompt.agentName || '-' }} / {{ prompt.model || '-' }}</summary>
            <pre class="admin-pre">{{ formatJson(prompt) }}</pre>
          </details>
        </div>
      </div>

      <div class="admin-grid-two">
        <div class="admin-card">
          <h2 class="admin-card-title">Usage</h2>
          <pre class="admin-pre">{{ formatJson(run.usage) }}</pre>
        </div>
        <div class="admin-card">
          <h2 class="admin-card-title">Model Output</h2>
          <pre class="admin-pre">{{ formatJson(run.output) }}</pre>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { opsAgentApi, type AgentDebugRunDetail } from '@/api/opsAgent'
import { formatJson, formatTokens, statusLabel } from './opsAgentView'

const route = useRoute()
const run = ref<AgentDebugRunDetail | null>(null)
const loading = ref(false)
const error = ref('')

async function loadRun() {
  loading.value = true
  error.value = ''
  try {
    run.value = await opsAgentApi.getRun(String(route.params.id))
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Run 详情加载失败'
  } finally {
    loading.value = false
  }
}

async function copyDebugJson() {
  if (!run.value) return
  await navigator.clipboard?.writeText(formatJson(run.value))
}

onMounted(() => {
  void loadRun()
})
</script>
