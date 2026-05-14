<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">权益流水</h1>
          <p class="admin-subtle">追踪额度增加、扣减、过期和管理员调整记录。当前为前端页面骨架。</p>
        </div>
        <span class="admin-badge">接口待接入</span>
      </div>
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input class="admin-input" disabled placeholder="搜索用户" />
          <select class="admin-select" disabled><option>全部类型</option></select>
          <input class="admin-input admin-input--sm" disabled placeholder="时间范围" />
        </div>
        <button class="admin-btn" disabled>查询</button>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead><tr><th>用户</th><th>类型</th><th>变更数量</th><th>来源</th><th>关联资源</th><th>时间</th></tr></thead>
          <tbody><tr class="admin-empty-row"><td colspan="6">权益流水接口接入后展示真实变更记录。</td></tr></tbody>
        </table>
      </div>
    </div>
  </section>
</template>

