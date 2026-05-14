<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">兑换码</h1>
          <p class="admin-subtle">管理订阅兑换码批次、兑换进度和停用状态。当前后端已有生成接口，列表接口待接入。</p>
        </div>
        <button class="admin-btn" disabled title="待补齐兑换码列表和创建表单">生成兑换码</button>
      </div>
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input class="admin-input" disabled placeholder="搜索批次号" />
          <select class="admin-select" disabled><option>全部状态</option></select>
          <input class="admin-input admin-input--sm" disabled placeholder="创建时间" />
        </div>
        <button class="admin-btn admin-btn--secondary" disabled>查询</button>
      </div>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead><tr><th>批次</th><th>套餐</th><th>总数量</th><th>已兑换</th><th>状态</th><th>创建时间</th></tr></thead>
          <tbody><tr class="admin-empty-row"><td colspan="6">兑换码列表接口接入后展示真实批次。</td></tr></tbody>
        </table>
      </div>
    </div>
  </section>
</template>

