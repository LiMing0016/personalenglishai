<template>
  <div class="home-container">
    <div class="header">
      <h1>Personal English AI</h1>
      <p class="subtitle">欢迎使用英语学习助手</p>
    </div>
    <div class="content">
      <div class="card">
        <h2>功能说明</h2>
        <p>这是首页占位页面，用于验证路由和登录状态。</p>
        <div class="actions">
          <router-link to="/login" class="link">登录</router-link>
          <router-link to="/register" class="link">注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Home page
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  padding: 40px 20px;
}

.header {
  text-align: center;
  margin-bottom: 40px;
}

.header h1 {
  margin: 0 0 8px 0;
  font-size: 32px;
  font-weight: 600;
  color: #333;
}

.subtitle {
  margin: 0;
  color: #666;
  font-size: 16px;
}

.content {
  max-width: 800px;
  margin: 0 auto;
}

.card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 32px;
}

.card h2 {
  margin: 0 0 16px 0;
  font-size: 24px;
  color: #333;
}

.card p {
  margin: 0 0 24px 0;
  color: #666;
  line-height: 1.6;
}

.actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.link {
  padding: 12px 24px;
  background: #1976d2;
  color: white;
  text-decoration: none;
  border-radius: 6px;
  font-weight: 500;
  transition: background 0.2s;
}

.link:hover {
  background: #1565c0;
}
</style>

