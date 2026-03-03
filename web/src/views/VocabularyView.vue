<template>
  <div class="vocab-page">
    <div class="vocab-header">
      <h1 class="page-title">背单词</h1>
      <div class="stats-bar">
        <span class="stat">今日: <strong>0 / 30</strong></span>
        <span class="stat">待复习: <strong>0</strong></span>
      </div>
    </div>

    <div class="card-area">
      <div class="word-card">
        <span class="word">elaborate</span>
        <span class="phonetic">/ɪˈlæbərət/</span>
        <div class="meaning hidden-meaning">
          <p><strong>adj.</strong> 精心制作的；详尽的</p>
          <p><strong>v.</strong> 详细阐述</p>
          <p class="example">She elaborated on her plans for the future.</p>
        </div>
        <button type="button" class="btn-reveal" @click="revealed = !revealed">
          {{ revealed ? '隐藏释义' : '显示释义' }}
        </button>
      </div>

      <div class="action-row">
        <button type="button" class="btn-know btn-action" disabled>认识</button>
        <button type="button" class="btn-fuzzy btn-action" disabled>模糊</button>
        <button type="button" class="btn-unknown btn-action" disabled>不认识</button>
      </div>
    </div>

    <div class="coming-soon">
      <p>单词学习模块开发中，敬请期待。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const revealed = ref(false)
</script>

<style scoped>
.vocab-page {
  max-width: 560px;
  margin: 0 auto;
  padding: 32px 24px;
}

.vocab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.stats-bar {
  display: flex;
  gap: 16px;
}

.stat {
  font-size: 13px;
  color: #6b7280;
}

.stat strong {
  color: #111827;
}

.card-area {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.word-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 24px 28px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  margin-bottom: 20px;
}

.word {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 6px;
}

.phonetic {
  font-size: 15px;
  color: #9ca3af;
  margin-bottom: 20px;
}

.meaning {
  text-align: center;
  font-size: 15px;
  color: #374151;
  line-height: 1.7;
  margin-bottom: 16px;
}

.meaning p {
  margin: 0 0 4px;
}

.example {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  font-style: italic;
}

.btn-reveal {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 10px;
  cursor: pointer;
}

.btn-reveal:hover {
  background: #d1fae5;
}

.action-row {
  display: flex;
  gap: 12px;
  width: 100%;
  margin-bottom: 28px;
}

.btn-action {
  flex: 1;
  padding: 12px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-know { background: #d1fae5; color: #065f46; }
.btn-fuzzy { background: #fef9c3; color: #92400e; }
.btn-unknown { background: #fee2e2; color: #991b1b; }

.coming-soon {
  text-align: center;
  padding: 20px;
  font-size: 14px;
  color: #9ca3af;
}

.coming-soon p {
  margin: 0;
}
</style>
