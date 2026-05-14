<template>
  <section class="admin-section ops-agent-page">
    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Eval Cases</div>
        <p class="admin-subtle">从真实 Agent 请求中挑选样本，沉淀为 route_eval、scoring_eval、feedback_eval 和 out_of_scope_eval。</p>
      </div>
      <button class="admin-btn" disabled>新建 Eval Case</button>
    </div>

    <div class="admin-grid-three">
      <div v-for="item in caseTypes" :key="item.key" class="admin-card ops-agent-case-card">
        <div class="admin-card-title">{{ item.title }}</div>
        <p class="admin-subtle">{{ item.description }}</p>
        <span class="admin-badge">{{ item.key }}</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const caseTypes = [
  { key: 'route_eval', title: '路由评测', description: '判断 intent、route_type、workflow 和 target_agent 是否正确。' },
  { key: 'scoring_eval', title: '评分评测', description: '判断分数区间、维度覆盖和评分理由是否稳定。' },
  { key: 'feedback_eval', title: '反馈评测', description: '判断反馈是否指出关键问题，并给出可执行建议。' },
  { key: 'out_of_scope_eval', title: '越界评测', description: '判断非学习请求是否被正确拒绝或轻量处理。' },
]
</script>

