<template>
  <div class="archive-panel">
    <section class="archive-hero">
      <span class="archive-icon" aria-hidden="true">
        <svg width="30" height="30" viewBox="0 0 32 32" fill="none">
          <path d="M3 9a3 3 0 0 1 3-3h7l3 4h10a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9z" stroke="currentColor" stroke-width="1.8" fill="white"/>
          <path d="M3 13h26v11a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V13z" fill="currentColor" opacity="0.12"/>
          <circle cx="23" cy="22" r="6" fill="currentColor"/>
          <path d="M20 22l2 2 4-4" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </span>
      <div>
        <p>{{ archived ? '这篇作文已进入作文资产库' : '把这篇作文沉淀为作文资产' }}</p>
        <strong>{{ title || '未命名作文' }}</strong>
      </div>
    </section>

    <div class="archive-status" :class="{ archived }">
      <span>{{ archived ? '已归档' : '未归档' }}</span>
      <em>{{ archived ? '会保留在用户中心的作文资产中，也可继续编辑。' : '归档不会隐藏作文，也不会影响评分和编辑。' }}</em>
    </div>

    <button
      v-if="!archived"
      class="archive-primary"
      type="button"
      :disabled="busy || !docId"
      @click="$emit('archive')"
    >
      {{ busy ? '归档中...' : '归档当前作文' }}
    </button>
    <button
      v-else
      class="archive-secondary"
      type="button"
      :disabled="busy || !docId"
      @click="$emit('unarchive')"
    >
      {{ busy ? '处理中...' : '取消归档' }}
    </button>

    <p v-if="!docId" class="archive-hint">当前作文尚未创建文档，保存后才能归档。</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  docId?: string | null
  title?: string | null
  archived: boolean
  busy: boolean
}>()

defineEmits<{
  archive: []
  unarchive: []
}>()
</script>

<style scoped>
.archive-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: #17201c;
}

.archive-hero {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border: 1px solid #d8eadf;
  border-radius: 8px;
  background: #f7fbf8;
}

.archive-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  color: #0f766e;
  background: #e8f6ef;
}

.archive-hero p {
  margin: 0 0 4px;
  color: #5f6f68;
  font-size: 13px;
}

.archive-hero strong {
  display: -webkit-box;
  overflow: hidden;
  color: #111827;
  font-size: 15px;
  line-height: 1.4;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.archive-status {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f6f3ec;
}

.archive-status.archived {
  background: #ecfdf5;
}

.archive-status span {
  color: #1f2937;
  font-size: 14px;
  font-weight: 800;
}

.archive-status em,
.archive-hint {
  color: #6b7280;
  font-size: 13px;
  font-style: normal;
  line-height: 1.6;
}

.archive-primary,
.archive-secondary {
  width: 100%;
  min-height: 42px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}

.archive-primary {
  color: #fff;
  background: #0f766e;
}

.archive-secondary {
  color: #0f766e;
  background: #e8f6ef;
}

.archive-primary:disabled,
.archive-secondary:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.archive-hint {
  margin: 0;
}
</style>
