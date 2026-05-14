<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h1 class="admin-card-title">管理员权限</h1>
          <p class="admin-subtle">集中查看管理员账号、角色和权限。当前角色修改仍在用户详情页完成。</p>
        </div>
        <router-link class="admin-btn admin-btn--secondary" to="/admin/users">去用户列表</router-link>
      </div>
      <div class="admin-empty">管理员权限中心待接入，现阶段请在用户详情中维护 super_admin、support_admin、content_admin。</div>
    </div>
  </section>
</template>
