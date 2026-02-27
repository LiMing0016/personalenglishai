<template>
  <div class="archive-panel">
    <p class="desc">将本次写作保存到归档，可添加标题或标签便于日后查找。</p>
    <div class="field">
      <label class="field-label">标题（可选）</label>
      <input v-model="title" type="text" class="field-input" placeholder="输入归档标题" />
    </div>
    <div class="field">
      <label class="field-label">标签（可选）</label>
      <input v-model="tags" type="text" class="field-input" placeholder="多个标签用逗号分隔" />
    </div>
    <button type="button" class="btn-save" @click="onSave">保存归档</button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  archived: []
}>()

const title = ref('')
const tags = ref('')

function onSave() {
  emit('archived')
}
</script>

<style scoped>
.archive-panel {
  padding: 16px;
}
.desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  margin: 0 0 16px;
}
.field {
  margin-bottom: 14px;
}
.field-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}
.field-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #111827;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  outline: none;
  box-sizing: border-box;
}
.field-input::placeholder {
  color: #9ca3af;
}
.field-input:focus {
  border-color: #047857;
}
.btn-save {
  margin-top: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 12px;
  cursor: pointer;
}
.btn-save:hover {
  background: #065f46;
}
</style>
