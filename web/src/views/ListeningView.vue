<template>
  <div class="listening-page">
    <h1 class="page-title">听力练习</h1>

    <div class="player-card">
      <div class="question">
        <span class="q-label">Q1</span>
        <span class="q-text">What does the speaker plan to do this weekend?</span>
      </div>

      <div class="audio-bar">
        <button type="button" class="btn-play" disabled>&#9654;</button>
        <div class="progress-track">
          <div class="progress-fill" style="width: 0%"></div>
        </div>
        <span class="time">0:00 / 0:45</span>
      </div>

      <div class="speed-row">
        <button type="button" class="speed-btn" disabled>0.75x</button>
        <button type="button" class="speed-btn active" disabled>1x</button>
        <button type="button" class="speed-btn" disabled>1.25x</button>
      </div>

      <ul class="options">
        <li class="option">
          <span class="option-letter">A</span>
          <span>Go shopping with friends</span>
        </li>
        <li class="option">
          <span class="option-letter">B</span>
          <span>Visit her grandparents</span>
        </li>
        <li class="option">
          <span class="option-letter">C</span>
          <span>Stay home and study</span>
        </li>
        <li class="option">
          <span class="option-letter">D</span>
          <span>Travel to another city</span>
        </li>
      </ul>

      <button type="button" class="btn-submit" disabled>确认答案</button>
    </div>

    <div class="coming-soon">
      <p>听力练习模块开发中，敬请期待。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.listening-page {
  max-width: 620px;
  margin: 0 auto;
  padding: 32px 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 24px;
}

.player-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 20px;
}

.question {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.q-label {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 700;
  color: #047857;
  background: #ecfdf5;
  border-radius: 6px;
}

.q-text {
  font-size: 15px;
  color: #111827;
  line-height: 1.5;
}

.audio-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.btn-play {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #047857;
  color: #fff;
  font-size: 14px;
  cursor: not-allowed;
  opacity: 0.5;
}

.progress-track {
  flex: 1;
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #047857;
  border-radius: 2px;
}

.time {
  font-size: 12px;
  color: #9ca3af;
  white-space: nowrap;
}

.speed-row {
  display: flex;
  gap: 6px;
  margin-bottom: 20px;
}

.speed-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  cursor: not-allowed;
  opacity: 0.5;
}

.speed-btn.active {
  background: #f3f4f6;
  color: #111827;
  font-weight: 600;
}

.options {
  list-style: none;
  padding: 0;
  margin: 0 0 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  color: #374151;
  cursor: default;
}

.option-letter {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 6px;
}

.btn-submit {
  width: 100%;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 12px;
  cursor: not-allowed;
  opacity: 0.5;
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
