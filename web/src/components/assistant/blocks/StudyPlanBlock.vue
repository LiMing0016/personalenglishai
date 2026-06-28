<template>
  <article class="learning-block study-plan">
    <header class="block-header">
      <div>
        <p class="block-kicker">学习规划</p>
        <h3>{{ block.title || data.title }}</h3>
      </div>
      <span v-if="data.durationDays" class="duration-badge">{{ data.durationDays }} 天</span>
    </header>

    <p v-if="data.goal" class="plan-goal">{{ data.goal }}</p>

    <ol v-if="data.days?.length" class="day-list">
      <li v-for="day in data.days" :key="day.day" class="day-row">
        <div class="day-index">D{{ day.day }}</div>
        <div class="day-main">
          <div class="day-heading">
            <p class="day-title">{{ day.title }}</p>
            <span v-if="day.focus" class="day-focus">{{ day.focus }}</span>
          </div>

          <ul v-if="day.tasks?.length" class="task-list">
            <li v-for="task in day.tasks" :key="task.title">
              <span class="task-title">{{ task.title }}</span>
              <span v-if="task.minutes" class="task-meta">{{ task.minutes }}min</span>
              <span v-if="task.output" class="task-output">{{ task.output }}</span>
            </li>
          </ul>

          <p v-if="day.check" class="day-check">{{ day.check }}</p>
        </div>
      </li>
    </ol>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { StudyPlanBlock } from '@/types/assistantBlocks.ts'

const props = defineProps<{
  block: StudyPlanBlock
}>()

const data = computed(() => props.block.data)
</script>

<style scoped>
.learning-block {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
  color: #0f172a;
}

.study-plan {
  padding: 18px;
}

.block-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.block-kicker {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 850;
}

.block-header h3 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
}

.duration-badge {
  flex-shrink: 0;
  border-radius: 8px;
  background: #eef2ff;
  color: #3730a3;
  padding: 6px 9px;
  font-size: 12px;
  font-weight: 850;
}

.plan-goal {
  margin: 12px 0 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.6;
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.day-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  border-top: 1px solid #e2e8f0;
  padding-top: 14px;
}

.day-row:first-child {
  border-top: none;
  padding-top: 0;
}

.day-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 34px;
  border-radius: 8px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.day-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.day-title {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 850;
}

.day-focus {
  color: #64748b;
  font-size: 12px;
  font-weight: 650;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.task-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  color: #334155;
  font-size: 13px;
  line-height: 1.5;
}

.task-title {
  font-weight: 700;
}

.task-meta,
.task-output {
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  padding: 2px 7px;
  font-size: 12px;
}

.day-check {
  margin: 10px 0 0;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}
</style>
