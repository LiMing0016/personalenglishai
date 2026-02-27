<template>
  <div class="polish-panel">
    <section class="section">
      <h4 class="section-title">高级词替换建议</h4>
      <ul class="suggestion-list">
        <li v-for="(s, i) in wordSuggestions" :key="i" class="suggestion-item">
          <span class="original">{{ s.original }}</span>
          <span class="arrow">→</span>
          <span class="replacement">{{ s.replacement }}</span>
        </li>
      </ul>
    </section>
    <section class="section">
      <h4 class="section-title">句子改写建议</h4>
      <ul class="suggestion-list">
        <li v-for="(s, i) in sentenceSuggestions" :key="i" class="suggestion-item block">
          <p class="original-p">原句：{{ s.original }}</p>
          <p class="replacement-p">改写：{{ s.replacement }}</p>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
const wordSuggestions = [
  { original: 'good', replacement: 'excellent' },
  { original: 'important', replacement: 'significant' },
  { original: 'many', replacement: 'numerous' },
]
const sentenceSuggestions = [
  { original: 'I think that this is good.', replacement: 'In my view, this is commendable.' },
  { original: 'We need to do something.', replacement: 'It is necessary for us to take action.' },
]
</script>

<style scoped>
.polish-panel {
  padding: 16px;
}
.section {
  margin-bottom: 20px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin: 0 0 10px;
}
.suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.suggestion-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
  color: #374151;
}
.suggestion-item.block {
  flex-direction: column;
  align-items: flex-start;
  border-top: 1px solid #e5e7eb;
}
.suggestion-item:first-child.block {
  border-top: none;
}
.original {
  color: #6b7280;
}
.arrow {
  color: #9ca3af;
}
.replacement {
  color: #047857;
  font-weight: 500;
}
.original-p,
.replacement-p {
  margin: 0 0 4px;
  line-height: 1.5;
}
.replacement-p {
  color: #047857;
}
</style>
