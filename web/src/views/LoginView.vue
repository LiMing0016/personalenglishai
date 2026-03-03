<template>
  <div class="auth-split-page">
    <section class="split-pane pane-left" aria-label="Auth poster"></section>

    <div class="split-divider" aria-hidden="true"></div>

    <section class="split-pane pane-right" aria-label="Personal English AI auth entry">
      <button class="right-click-layer" type="button" @click="goLoginForm" aria-label="Open login form">
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import authFull from '@/assets/auth/auth-full.png'

const router = useRouter()
const authFullBg = `url(${authFull})`

function goLoginForm() {
  const redirect = typeof router.currentRoute.value.query.redirect === 'string'
    ? router.currentRoute.value.query.redirect
    : ''
  if (redirect) {
    router.push({ path: '/login-form', query: { redirect } })
    return
  }
  router.push('/login-form')
}
</script>

<style scoped>
.auth-split-page {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 1px minmax(0, 1fr);
  background: #0d1d5a;
}

.split-pane {
  min-width: 0;
  min-height: 100dvh;
  overflow: hidden;
  background-color: #0d1d5a;
  background-image: v-bind(authFullBg);
  background-repeat: no-repeat;
  background-size: 200% 100%;
}

.pane-left {
  background-position: left center;
}

.pane-right {
  background-position: right center;
}

.split-divider {
  width: 1px;
  background: rgba(255, 255, 255, 0.22);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.04);
}

.right-click-layer {
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  display: block;
}

.right-click-layer:focus-visible {
  outline: 3px solid rgba(126, 202, 255, 0.8);
  outline-offset: -3px;
}

@media (max-width: 980px) {
  .auth-split-page {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr 1px 1fr;
  }

  .split-divider {
    width: 100%;
    height: 1px;
  }

  .split-pane {
    min-height: 50dvh;
  }
}
</style>

