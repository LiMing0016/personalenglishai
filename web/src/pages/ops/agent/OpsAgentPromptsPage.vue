<template>
  <section class="admin-section ops-agent-page">
    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Prompt 调试</div>
        <p class="admin-subtle">后续展示每次调用实际渲染后的 system / developer / user prompt、prompt key、版本和 hash。</p>
      </div>
      <span class="admin-badge">Prompt Snapshots</span>
    </div>

    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input class="admin-input" disabled placeholder="搜索 prompt key / hash / agent" />
          <select class="admin-select" disabled>
            <option>全部模型</option>
          </select>
        </div>
        <button class="admin-btn" disabled>查询</button>
      </div>

      <div class="admin-empty">Prompt snapshot 接入后显示真实 Prompt 记录。</div>
    </div>
  </section>
</template>
