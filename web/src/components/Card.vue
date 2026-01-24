<template>
  <div class="card">
    <slot />
  </div>
</template>

<script setup lang="ts">
// Card component
</script>

<style scoped>
.card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 32px;
  max-width: 400px;
  width: 100%;
  box-sizing: border-box;
}
</style>

