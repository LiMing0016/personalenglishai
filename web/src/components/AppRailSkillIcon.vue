<template>
  <span class="skill-logo" :class="`skill-logo--${icon}`" aria-hidden="true">
    <svg v-if="icon === 'listening'" viewBox="0 0 48 48">
      <path class="stroke stroke-a" d="M27 15c4.6-5.8 14.5-2.4 14.5 7.8 0 9.8-7.4 11.4-8.5 17.1" />
      <path class="stroke stroke-b" d="M28.5 35.5c0-5 5.5-5.7 5.5-12.5 0-5.2-5.7-7.4-8.8-3.2-2.3 3.1-.6 7.2 3.3 7.2" />
      <path class="stroke stroke-a bars" d="M7 25v4M13 21v12M19 17v20" />
    </svg>

    <svg v-else-if="icon === 'speaking'" viewBox="0 0 48 48">
      <path class="fill fill-a" d="M18 20a6 6 0 0 1 12 0v11a6 6 0 0 1-12 0V20Z" />
      <path class="stroke stroke-b" d="M12 27v3a12 12 0 0 0 24 0v-3M24 42v-7M17 42h14" />
      <path class="bubble" d="M16 18.5h16v10H22l-6 4v-14Z" />
      <path class="stroke stroke-c" d="M21 23h.1M24 23h.1M27 23h.1" />
    </svg>

    <svg v-else-if="icon === 'reading'" viewBox="0 0 48 48">
      <path class="book book-left" d="M8 18c6-1.6 12-.8 16 3v18c-4-3.8-10-4.6-16-3V18Z" />
      <path class="book book-right" d="M40 18c-6-1.6-12-.8-16 3v18c4-3.8 10-4.6 16-3V18Z" />
      <path class="stroke stroke-b" d="M8 36c6-1.6 12-.8 16 3 4-3.8 10-4.6 16-3" />
      <path class="stroke stroke-a" d="M16 24c2.4.1 4.1.7 5.6 1.8M30.4 25.8c1.5-1.1 3.2-1.7 5.6-1.8" />
      <path class="eye" d="M18 12c3.8-3.2 8.2-3.2 12 0-3.8 3.2-8.2 3.2-12 0Z" />
      <circle class="pupil" cx="24" cy="12" r="2.6" />
    </svg>

    <svg v-else viewBox="0 0 48 48">
      <path class="paper" d="M13 11h21l3 3v23H13V11Z" />
      <path class="stroke stroke-c" d="M18 19h12M18 25h10M18 31h7" />
      <path class="pen-body" d="M30.5 13.5 38 18l-12.8 21-7.5 2.3 2.3-7.5 10.5-20.3Z" />
      <path class="pen-tip" d="m17.7 41.3 2.3-7.5 5.2 5.2-7.5 2.3Z" />
      <path class="stroke stroke-b" d="M9 37c4-4 7.2-2.7 10.4-5.7" />
    </svg>
  </span>
</template>

<script setup lang="ts">
defineProps<{
  icon: 'writing' | 'reading' | 'listening' | 'speaking'
}>()
</script>

<style scoped>
.skill-logo {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(226, 232, 240, 0.82);
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.skill-logo svg {
  width: 28px;
  height: 28px;
  overflow: visible;
}

.stroke {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 4;
}

.stroke-a {
  stroke: #1d82f3;
}

.stroke-b {
  stroke: #2563eb;
}

.stroke-c {
  stroke: #9fb0d5;
  stroke-width: 3;
}

.bars {
  stroke: #10d9bd;
}

.fill-a,
.book-right,
.pen-body {
  fill: #217cf4;
}

.bubble,
.paper {
  fill: #ffffff;
}

.book-left {
  fill: #26d2bc;
}

.eye {
  fill: #60a5fa;
}

.pupil {
  fill: #183a78;
}

.pen-tip {
  fill: #dbeafe;
}

</style>
