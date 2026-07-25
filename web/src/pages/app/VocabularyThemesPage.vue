<template>
  <main class="vocabulary-themes-page">
    <VocabularyThemeLibrary :theme-state="themeState" />
  </main>
</template>

<script setup lang="ts">
import VocabularyThemeLibrary from '@/components/vocabulary/VocabularyThemeLibrary.vue'
import { useVocabularyThemes } from '@/composables/useVocabularyThemes'

const themeState = useVocabularyThemes()
</script>

<style scoped>
.vocabulary-themes-page {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 100%;
  padding: 32px clamp(18px, 4vw, 48px) 56px;
  background: #f8fafc;
}

@media (max-width: 640px) {
  .vocabulary-themes-page { padding: 20px 14px 40px; }
}
</style>
