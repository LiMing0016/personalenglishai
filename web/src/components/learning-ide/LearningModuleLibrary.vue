<template>
  <section v-if="open" class="learning-module-library" aria-label="模块库">
    <header>
      <div>
        <strong>模块库</strong>
        <span>用户按需添加学习工具</span>
      </div>
      <button type="button" aria-label="关闭模块库" @click="emit('close')">×</button>
    </header>

    <div class="learning-module-library__groups">
      <section v-for="group in groups" :key="group.id" class="learning-module-group">
        <div class="learning-module-group__title">
          <strong>{{ group.label }}</strong>
          <span>{{ group.description }}</span>
        </div>
        <button
          v-for="module in group.modules"
          :key="module.id"
          type="button"
          class="learning-module-card"
          :class="`learning-module-card--${module.status}`"
          @click="emit('addModule', module.id)">
          <span aria-hidden="true">{{ module.icon }}</span>
          <span>
            <strong>{{ module.label }}</strong>
            <small>{{ module.description }}</small>
          </span>
          <mark>{{ module.status === 'enabled' ? '已启用' : '添加' }}</mark>
        </button>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { LearningModuleGroup } from '../../types/learningIde'

defineProps<{
  open: boolean
  groups: LearningModuleGroup[]
}>()

const emit = defineEmits<{
  addModule: [moduleId: string]
  close: []
}>()
</script>

<style scoped>
.learning-module-library {
  position: absolute;
  top: 64px;
  left: min(48vw, 680px);
  z-index: 30;
  display: grid;
  width: min(360px, calc(100vw - 32px));
  max-height: calc(100vh - 96px);
  overflow: hidden;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.16);
  color: #102033;
}

.learning-module-library header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px;
  border-bottom: 1px solid #e5edf4;
}

.learning-module-library header div {
  display: grid;
  gap: 3px;
}

.learning-module-library span,
.learning-module-library small {
  color: #667085;
  font-size: 12px;
}

.learning-module-library button {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #102033;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.learning-module-library header button {
  width: 32px;
  min-height: 32px;
}

.learning-module-library__groups {
  display: grid;
  gap: 14px;
  min-height: 0;
  overflow: auto;
  padding: 14px;
}

.learning-module-group,
.learning-module-group__title {
  display: grid;
  gap: 8px;
}

.learning-module-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 58px;
  padding: 8px;
  text-align: left;
}

.learning-module-card > span:first-child {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 7px;
  background: #eef7f6;
  color: #0f8f89;
  font-size: 11px;
}

.learning-module-card > span:nth-child(2) {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.learning-module-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.learning-module-card mark {
  border-radius: 999px;
  background: #eaf2ff;
  color: #2563eb;
  padding: 2px 7px;
  font-size: 11px;
}

.learning-module-card--enabled mark {
  background: #e6f7ef;
  color: #0f8f89;
}
</style>
