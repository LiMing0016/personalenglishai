<template>
  <div class="speaking-page">
    <h1 class="page-title">口语练习</h1>

    <div class="topic-card">
      <span class="topic-label">Today's Topic</span>
      <h2 class="topic-text">Describe your favorite place to visit and explain why you like it.</h2>
      <div class="topic-meta">
        <span class="meta-chip">Preparation: 45s</span>
        <span class="meta-chip">Speaking: 60s</span>
      </div>
    </div>

    <div class="record-area">
      <div class="mic-circle">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>
      </div>
      <p class="record-hint">点击麦克风开始录音（即将上线）</p>
    </div>

    <div class="result-preview">
      <div class="result-card">
        <span class="result-title">AI 评价预览</span>
        <div class="score-row">
          <div class="score-dim">
            <span class="dim-label">流利度</span>
            <span class="dim-stars">--</span>
          </div>
          <div class="score-dim">
            <span class="dim-label">发音</span>
            <span class="dim-stars">--</span>
          </div>
          <div class="score-dim">
            <span class="dim-label">内容</span>
            <span class="dim-stars">--</span>
          </div>
          <div class="score-dim">
            <span class="dim-label">语法</span>
            <span class="dim-stars">--</span>
          </div>
        </div>
      </div>
    </div>

    <div class="coming-soon">
      <p>口语练习模块开发中，敬请期待。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.speaking-page {
  max-width: 560px;
  margin: 0 auto;
  padding: 32px 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 24px;
}

.topic-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 24px;
}

.topic-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.topic-text {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  line-height: 1.5;
  margin: 10px 0 14px;
}

.topic-meta {
  display: flex;
  gap: 8px;
}

.meta-chip {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #6b7280;
}

.record-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
}

.mic-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #f3f4f6;
  border: 2px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  margin-bottom: 12px;
}

.record-hint {
  font-size: 14px;
  color: #9ca3af;
  margin: 0;
}

.result-preview {
  margin-bottom: 20px;
}

.result-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 18px 20px;
}

.result-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 14px;
}

.score-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.score-dim {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.dim-label {
  font-size: 12px;
  color: #6b7280;
}

.dim-stars {
  font-size: 16px;
  font-weight: 700;
  color: #d1d5db;
}

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
