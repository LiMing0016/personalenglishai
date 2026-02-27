<template>
  <div class="fix-panel">
    <div class="strategy">
      <span class="strategy-label">订正策略</span>
      <div class="radio-group">
        <label><input v-model="strategy" type="radio" value="conservative" /> 保守</label>
        <label><input v-model="strategy" type="radio" value="standard" /> 标准</label>
        <label><input v-model="strategy" type="radio" value="aggressive" /> 激进</label>
      </div>
    </div>
    <ul class="fix-list">
      <li v-for="(item, i) in items" :key="i" class="fix-item">
        <span class="fix-type">{{ item.type }}</span>
        <p class="fix-original">{{ item.original }}</p>
        <p class="fix-suggestion">建议：{{ item.suggestion }}</p>
      </li>
    </ul>
    <button type="button" class="btn-apply" @click="onApply">应用订正</button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { showToast } from '@/utils/toast'

const strategy = ref<'conservative' | 'standard' | 'aggressive'>('standard')

const items = [
  { type: '时态', original: 'Yesterday I go to the library.', suggestion: 'Yesterday I went to the library.' },
  { type: '冠词', original: 'I saw a owl in the tree.', suggestion: 'I saw an owl in the tree.' },
  { type: '搭配', original: 'I made my homework.', suggestion: 'I did my homework.' },
]

function onApply() {
  showToast('订正已应用（mock）', 'info')
}
</script>

<style scoped>
.fix-panel {
  padding: 16px;
}
.strategy {
  margin-bottom: 16px;
}
.strategy-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}
.radio-group {
  display: flex;
  gap: 16px;
}
.radio-group label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
}
.fix-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
}
.fix-item {
  padding: 12px 0;
  border-top: 1px solid #e5e7eb;
  font-size: 13px;
}
.fix-item:first-child {
  border-top: none;
}
.fix-type {
  display: inline-block;
  padding: 2px 8px;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 6px;
}
.fix-original {
  margin: 0 0 4px;
  color: #374151;
}
.fix-suggestion {
  margin: 0;
  color: #047857;
}
.btn-apply {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 12px;
  cursor: pointer;
}
.btn-apply:hover {
  background: #065f46;
}
</style>
