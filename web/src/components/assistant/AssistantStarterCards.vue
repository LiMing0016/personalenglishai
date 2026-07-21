<template>
  <div class="starter-experience">
    <div class="starter-goals" aria-label="选择学习目标">
      <button
        v-for="goal in goals"
        :key="goal.id"
        type="button"
        class="starter-goal"
        :class="{ 'starter-goal--selected': selectedGoal === goal.id }"
        :aria-pressed="selectedGoal === goal.id"
        @click="$emit('selectGoal', goal.id)"
      >
        {{ goal.label }}
      </button>
    </div>

    <div class="starter-composer">
      <slot name="composer"></slot>
    </div>

    <div class="starter-examples" aria-label="示例问题">
      <button
        v-for="example in examples"
        :key="example.prompt"
        type="button"
        class="starter-example"
        @click="$emit('choose', example.prompt)"
      >
        <span class="starter-example-prompt">{{ example.prompt }}</span>
        <span class="starter-example-outcome">{{ example.outcome }}</span>
        <span class="starter-example-arrow" aria-hidden="true">→</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
export type AssistantStarterGoalId = 'check' | 'polish' | 'practice' | 'explain'

defineProps<{
  selectedGoal: AssistantStarterGoalId | null
}>()

defineEmits<{
  selectGoal: [goalId: AssistantStarterGoalId]
  choose: [prompt: string]
}>()

const goals = [
  { id: 'check', label: '检查句子' },
  { id: 'polish', label: '润色表达' },
  { id: 'practice', label: '设计练习' },
  { id: 'explain', label: '讲解词句' },
] as const

const examples = [
  { prompt: '检查这句话是否自然', outcome: '给出原因和改法' },
  { prompt: '帮我升级这段表达', outcome: '保留原意，更地道' },
  { prompt: '设计一道写作练习', outcome: '包含题目、思路和反馈' },
] as const
</script>

<style scoped>
.starter-experience {
  display: flex;
  width: min(680px, 100%);
  flex-direction: column;
  gap: 18px;
}

.starter-goals {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.starter-goal {
  min-height: 36px;
  padding: 8px 15px;
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 140ms ease, background-color 140ms ease, color 140ms ease;
}

.starter-goal:hover,
.starter-goal:focus-visible,
.starter-goal--selected {
  border-color: #86efac;
  background: #ecfdf5;
  color: #047857;
  outline: none;
}

.starter-composer {
  width: 100%;
}

.starter-examples {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
}

.starter-example {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) minmax(160px, auto) 20px;
  align-items: center;
  gap: 16px;
  min-height: 58px;
  padding: 14px 18px;
  border: none;
  border-bottom: 1px solid #e2e8f0;
  background: transparent;
  color: #0f172a;
  text-align: left;
  cursor: pointer;
}

.starter-example:last-child {
  border-bottom: none;
}

.starter-example:hover,
.starter-example:focus-visible {
  background: #f8fafc;
  outline: none;
}

.starter-example-prompt {
  min-width: 0;
  font-size: 14px;
  font-weight: 750;
}

.starter-example-outcome {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.starter-example-arrow {
  color: #94a3b8;
  font-size: 17px;
}

@media (max-width: 640px) {
  .starter-goals {
    justify-content: flex-start;
  }

  .starter-example {
    grid-template-columns: minmax(0, 1fr) 18px;
    gap: 10px;
  }

  .starter-example-outcome {
    grid-column: 1;
    grid-row: 2;
  }

  .starter-example-arrow {
    grid-column: 2;
    grid-row: 1 / span 2;
  }
}
</style>
