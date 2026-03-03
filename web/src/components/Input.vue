<template>
  <div class="input-wrapper">
    <label v-if="label" class="input-label">{{ label }}</label>
    <div class="input-shell" :class="{ 'has-toggle': canTogglePassword }">
      <input
        :type="currentType"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :class="['input', { 'input-error': error }]"
        @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
        @blur="$emit('blur', $event)"
      />
      <button
        v-if="canTogglePassword"
        type="button"
        class="toggle-btn"
        :disabled="disabled"
        @click="passwordVisible = !passwordVisible"
      >
        {{ passwordVisible ? '隐藏' : '显示' }}
      </button>
    </div>
    <span v-if="error" class="input-error-text">{{ error }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  modelValue: string
  type?: string
  label?: string
  placeholder?: string
  error?: string
  disabled?: boolean
  showPasswordToggle?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string]
  blur: [event: FocusEvent]
}>()

const passwordVisible = ref(false)

const canTogglePassword = computed(
  () => props.showPasswordToggle && (props.type ?? 'text') === 'password'
)

const currentType = computed(() => {
  if (!canTogglePassword.value) {
    return props.type ?? 'text'
  }
  return passwordVisible.value ? 'text' : 'password'
})
</script>

<style scoped>
.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-shell {
  position: relative;
}

.input-shell.has-toggle .input {
  padding-right: 68px;
}

.input-label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.input {
  width: 100%;
  padding: 12px 16px;
  font-size: 16px;
  border: 1px solid #ddd;
  border-radius: 6px;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #1976d2;
}

.input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.toggle-btn {
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  border: none;
  background: transparent;
  color: #1976d2;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 2px 4px;
}

.toggle-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.input-error {
  border-color: #f44336;
}

.input-error-text {
  font-size: 12px;
  color: #f44336;
}
</style>
