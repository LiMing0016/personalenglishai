<template>
  <section class="admin-section ops-agent-page">
    <div class="admin-card ops-agent-hero">
      <div>
        <div class="admin-card-title">Agent 请求记录</div>
        <p class="admin-subtle">后续接入 Agent Debug Recorder 后，这里会展示每次请求的 RouteRequest、RoutingDecision、模型输出、usage 和 trace 链接。</p>
      </div>
      <span class="admin-badge">P0 空壳</span>
    </div>

    <div class="admin-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <input class="admin-input" disabled placeholder="搜索 run id / 用户 / 会话 / intent" />
          <select class="admin-select" disabled>
            <option>全部状态</option>
          </select>
          <select class="admin-select" disabled>
            <option>全部 Agent</option>
          </select>
        </div>
        <button class="admin-btn" disabled>查询</button>
      </div>

      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>用户输入</th>
              <th>Intent</th>
              <th>Workflow / Agent</th>
              <th>模型</th>
              <th>Tokens</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr class="admin-empty-row">
              <td colspan="7">Debug Recorder 接入后显示真实请求记录。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

