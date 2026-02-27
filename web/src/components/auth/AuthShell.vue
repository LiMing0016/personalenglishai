<template>
  <div class="auth-shell">
    <div class="auth-stage">
      <section class="brand-pane" aria-hidden="true"></section>
      <div class="split-divider" aria-hidden="true"></div>

      <section class="form-pane" aria-label="Authentication form">
        <div class="form-pane-overlay"></div>
        <div class="form-panel-inner">
          <div class="form-brand">
            <div class="form-brand-logo-wrap">
              <img
                v-if="!logoMissing"
                class="form-brand-logo-image"
                :src="logoSrc"
                alt="Personal English AI logo"
                @error="logoMissing = true"
              />
              <div v-else class="form-brand-logo-fallback" aria-hidden="true">
                <span class="brand-logo-core"></span>
              </div>
            </div>
          <div class="form-brand-text">{{ panelBrand }}</div>
          </div>

          <h1 v-if="panelTitle" class="panel-title">{{ panelTitle }}</h1>
          <p v-if="panelSubtitle" class="panel-subtitle">{{ panelSubtitle }}</p>

          <slot />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import authFull from '@/assets/auth/auth-full.png'

withDefaults(
  defineProps<{
    panelBrand?: string
    panelTitle?: string
    panelSubtitle?: string
    logoSrc?: string
  }>(),
  {
    panelBrand: 'Personal English AI',
    panelTitle: '',
    panelSubtitle: '',
    logoSrc: '/brand/peai-logo.png',
  }
)

const logoMissing = ref(false)
const authFullBg = `url(${authFull})`
</script>

<style scoped>
.auth-shell {
  min-height: 100dvh;
  background: #071a58;
}

.auth-stage {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 1px minmax(0, 1fr);
}

.brand-pane,
.form-pane {
  min-width: 0;
  min-height: 100dvh;
  background-image: v-bind(authFullBg);
  background-repeat: no-repeat;
  background-size: 200% 100%;
  background-color: #071a58;
}

.brand-pane {
  background-position: left center;
}

.form-pane {
  position: relative;
  display: grid;
  place-items: center;
  padding: 32px 28px;
  background-position: right center;
}

.form-pane-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 52% 38%, rgba(255, 174, 49, 0.08), transparent 36%),
    linear-gradient(180deg, rgba(5, 18, 56, 0.32), rgba(5, 18, 56, 0.52));
  pointer-events: none;
}

.split-divider {
  width: 1px;
  background: rgba(255, 255, 255, 0.24);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.04);
}

.form-panel-inner {
  position: relative;
  z-index: 1;
  width: min(100%, 430px);
  border-radius: 20px;
  padding: 26px 24px 20px;
  color: #f8fbff;
  background: rgba(8, 20, 66, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(8px);
}

.form-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.form-brand-logo-wrap {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
}

.form-brand-logo-image {
  width: 46px;
  height: 46px;
  object-fit: contain;
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.28));
}

.form-brand-logo-fallback {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #ffb84d, #ff6a6a 35%, #6f6bff 70%, #35d6ff);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.22);
}

.brand-logo-core {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.95);
  position: relative;
  display: block;
}

.brand-logo-core::after {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: inherit;
  border: 2px solid rgba(255, 255, 255, 0.55);
}

.form-brand-text {
  font-size: 16px;
  color: rgba(244, 247, 255, 0.98);
  font-weight: 700;
}

.panel-title {
  margin: 16px 0 8px;
  font-size: clamp(24px, 2.2vw, 30px);
  line-height: 1.1;
  font-weight: 800;
  color: #fff;
}

.panel-subtitle {
  margin: 0 0 14px;
  color: rgba(220, 231, 249, 0.92);
  font-size: 13px;
  line-height: 1.45;
}

@media (max-width: 1180px) {
  .auth-stage {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(220px, 36dvh) 1px 1fr;
  }

  .brand-pane,
  .form-pane {
    background-size: cover;
  }

  .brand-pane {
    background-position: center top;
    min-height: 220px;
  }

  .form-pane {
    background-position: center center;
    padding: 18px 14px 24px;
  }

  .split-divider {
    width: 100%;
    height: 1px;
  }

  .form-panel-inner {
    width: min(100%, 560px);
    padding: 22px 18px 18px;
    border-radius: 18px;
  }
}
</style>
