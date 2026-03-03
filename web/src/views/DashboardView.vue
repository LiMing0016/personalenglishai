<template>
  <div class="dashboard">
    <!-- 顶部欢迎区 -->
    <section class="hero">
      <div class="hero-text">
        <h1 class="greeting">{{ greeting }}，准备好学习了吗？</h1>
        <p class="sub">坚持每天练习，进步看得见。</p>
      </div>
      <router-link to="/app/writing" class="hero-cta">开始写作练习</router-link>
    </section>

    <!-- 统计卡片 -->
    <section class="stats">
      <div class="stat-card" v-for="s in stats" :key="s.label">
        <div class="stat-icon" :style="{ background: s.bg }">{{ s.icon }}</div>
        <div class="stat-body">
          <span class="stat-value">{{ s.value }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </div>
      </div>
    </section>

    <!-- 模块入口 -->
    <section class="modules">
      <h2 class="section-title">学习模块</h2>
      <div class="module-grid">
        <router-link
          v-for="m in modules"
          :key="m.to"
          :to="m.to"
          class="module-card"
          :style="{ '--accent': m.accent }"
        >
          <div class="module-icon">{{ m.icon }}</div>
          <div class="module-info">
            <span class="module-name">{{ m.name }}</span>
            <span class="module-desc">{{ m.desc }}</span>
          </div>
          <span class="module-arrow">&#8250;</span>
        </router-link>
      </div>
    </section>

    <!-- 成长曲线占位 -->
    <section class="chart-section">
      <h2 class="section-title">能力趋势</h2>
      <div class="chart-card">
        <div class="chart-empty">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
          <p>完成更多练习后，这里将展示你的能力成长曲线</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const hour = new Date().getHours()
const greeting = hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'

const stats = [
  { icon: '✏️', label: '写作评分', value: '--', bg: '#ecfdf5' },
  { icon: '📖', label: '已学单词', value: '0', bg: '#eff6ff' },
  { icon: '🎧', label: '听力正确率', value: '--', bg: '#fef3c7' },
  { icon: '🎤', label: '口语评分', value: '--', bg: '#fce7f3' },
]

const modules = [
  { icon: '✏️', name: '写作练习', desc: 'AI 评分、纠错、改写', to: '/app/writing', accent: '#047857' },
  { icon: '📖', name: '背单词', desc: '间隔重复，高效记忆', to: '/app/vocabulary', accent: '#2563eb' },
  { icon: '🎧', name: '听力训练', desc: '真题听力，精听精练', to: '/app/listening', accent: '#d97706' },
  { icon: '🎤', name: '口语练习', desc: '话题朗读，AI 评价', to: '/app/speaking', accent: '#db2777' },
]
</script>

<style scoped>
.dashboard {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 24px 48px;
}

/* ── 欢迎区 ── */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 32px;
  background: linear-gradient(135deg, #047857 0%, #059669 100%);
  border-radius: 18px;
  margin-bottom: 24px;
  color: #fff;
}

.greeting {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.sub {
  margin: 6px 0 0;
  font-size: 14px;
  opacity: 0.85;
}

.hero-cta {
  flex-shrink: 0;
  padding: 10px 22px;
  font-size: 14px;
  font-weight: 600;
  color: #047857;
  background: #fff;
  border-radius: 10px;
  text-decoration: none;
  transition: background 0.15s;
}

.hero-cta:hover {
  background: #ecfdf5;
}

/* ── 统计卡片 ── */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 28px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  line-height: 1.1;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-top: 3px;
}

/* ── 模块入口 ── */
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 14px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 28px;
}

.module-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 18px;
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 14px;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.module-card:hover {
  border-color: var(--accent, #047857);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.module-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.module-card:hover .module-icon {
  background: #f0fdf4;
}

.module-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.module-name {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.module-desc {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}

.module-arrow {
  font-size: 20px;
  color: #d1d5db;
  flex-shrink: 0;
  transition: color 0.15s;
}

.module-card:hover .module-arrow {
  color: var(--accent, #047857);
}

/* ── 趋势图占位 ── */
.chart-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 14px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  gap: 12px;
}

.chart-empty p {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
  text-align: center;
  max-width: 300px;
  line-height: 1.5;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>
