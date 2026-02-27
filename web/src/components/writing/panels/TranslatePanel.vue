<template>
  <div class="translate-panel">
    <div class="target-lang">
      <span class="label">目标语言</span>
      <div class="radio-group">
        <label><input v-model="targetLang" type="radio" value="zh" /> 中文</label>
        <label><input v-model="targetLang" type="radio" value="en" /> 英文</label>
      </div>
    </div>
    <div class="output">
      <span class="output-label">译文（mock）</span>
      <p class="output-text">
        这是一段示例译文。实际接入后端后可在此展示翻译结果。支持中英互译。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const targetLang = ref<'zh' | 'en'>('zh')
</script>

<style scoped>
.translate-panel {
  padding: 16px;
}
.target-lang {
  margin-bottom: 16px;
}
.label {
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
.output {
  padding: 12px;
  background: #f9fafb;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}
.output-label {
  font-size: 12px;
  color: #6b7280;
  display: block;
  margin-bottom: 8px;
}
.output-text {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
  margin: 0;
}
</style>
