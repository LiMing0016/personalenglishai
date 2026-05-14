<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">模型用量</h1>
          <p class="admin-subtle">按模型、workflow 和日期统计 token、请求量、成本和失败率。当前为聚合接口占位页。</p>
        </div>
        <span class="admin-badge">接口待接入</span>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead><tr><th>模型</th><th>Workflow</th><th>Input Tokens</th><th>Output Tokens</th><th>成本</th><th>日期</th></tr></thead>
          <tbody><tr class="admin-empty-row"><td colspan="6">模型用量聚合接口接入后展示真实数据。</td></tr></tbody>
        </table>
      </div>
    </div>
  </section>
</template>

