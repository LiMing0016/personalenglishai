<template>
  <section class="admin-section ops-agent-page">
    <router-link :to="runsPath" class="admin-back-link">返回请求记录</router-link>

    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Agent Run 详情</div>
        <p class="admin-subtle">当前为占位页。后续会按 run id 展示完整 JSON、Prompt snapshots、模型输入输出、usage 和 OpenAI trace。</p>
      </div>
      <span class="admin-badge">{{ route.params.id }}</span>
    </div>

    <div class="admin-grid-two">
      <div class="admin-card">
        <h2 class="admin-card-title">RouteRequest</h2>
        <pre class="admin-pre">{{ placeholder.routeRequest }}</pre>
      </div>
      <div class="admin-card">
        <h2 class="admin-card-title">RoutingDecision</h2>
        <pre class="admin-pre">{{ placeholder.routingDecision }}</pre>
      </div>
    </div>

    <div class="admin-card">
      <h2 class="admin-card-title">Steps</h2>
      <div class="admin-empty">Debug step 数据接入后显示 RouteAgent、目标 Agent、Tool call 和 Workflow 链路。</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const runsPath = computed(() => route.path.startsWith('/admin/') ? '/admin/agent-debug/runs' : '/ops/agent/runs')

const placeholder = {
  routeRequest: JSON.stringify({ message: '用户输入', mode: 'daily_learning', stage: 'postgrad' }, null, 2),
  routingDecision: JSON.stringify({ intent: 'polish', route_type: 'run_workflow', target_agent: 'Polish Agent' }, null, 2),
}
</script>
