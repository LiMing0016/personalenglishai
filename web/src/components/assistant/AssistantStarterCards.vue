<template>
  <div class="starter-grid">
    <button
      v-for="starter in starters"
      :key="starter"
      type="button"
      class="starter-card"
      @click="$emit('choose', starter)"
    >
      <span class="starter-kicker">Try this</span>
      <span class="starter-text">{{ starter }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { assistantStarterPrompts } from '@/pages/app/assistantMock.ts'

const starters = assistantStarterPrompts

defineEmits<{
  choose: [prompt: string]
}>()
</script>

<style scoped>
.starter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  width: 100%;
}

.starter-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 128px;
  padding: 18px;
  border: 1px solid #d1fae5;
  border-radius: 20px;
  background:
    radial-gradient(circle at top right, rgba(167, 243, 208, 0.75), transparent 38%),
    linear-gradient(180deg, #ffffff 0%, #f0fdf4 100%);
  text-align: left;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.starter-card:hover {
  transform: translateY(-1px);
  border-color: #6ee7b7;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
}

.starter-kicker {
  font-size: 11px;
  font-weight: 700;
  color: #047857;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.starter-text {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
}

@media (max-width: 900px) {
  .starter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
