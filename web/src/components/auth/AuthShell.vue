<template>
  <div class="auth-shell">
    <div class="auth-stage">
      <section class="brand-pane" aria-hidden="true">
        <div class="brand-content">
          <div class="brand-logo-wrap">
            <img
              v-if="!logoMissing"
              class="brand-logo-image"
              :src="logoSrc"
              alt="Personal English AI logo"
              @error="logoMissing = true"
            />
            <div v-else class="brand-logo-fallback" aria-hidden="true">
              <span class="logo-letter">AI</span>
            </div>
          </div>
          <h2 class="brand-name">Personal English AI</h2>
          <p class="brand-slogan">Your Personal Path to Better Writing</p>
          <p class="brand-sub">个性英语老师</p>
        </div>
        <div class="brand-deco-line" aria-hidden="true"></div>
      </section>

      <div class="split-divider" aria-hidden="true"></div>

      <section class="form-pane" aria-label="Authentication form">
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
                <span class="logo-letter-sm">AI</span>
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
</script>

<style scoped>
.auth-shell {
  min-height: 100dvh;
  background: #0a1234;
}

.auth-stage {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 1px minmax(0, 1fr);
}

/* ── Left: Brand Pane ── */

.brand-pane {
  min-width: 0;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 60% at 30% 20%, rgba(53, 192, 255, 0.15), transparent),
    radial-gradient(ellipse 60% 50% at 70% 80%, rgba(164, 125, 255, 0.12), transparent),
    radial-gradient(ellipse 50% 40% at 50% 50%, rgba(111, 107, 255, 0.08), transparent),
    linear-gradient(160deg, #071a58 0%, #0d1b4a 30%, #1a1050 60%, #0e0a2e 100%);
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 0 32px;
}

.brand-logo-wrap {
  width: 72px;
  height: 72px;
  margin: 0 auto 24px;
  display: grid;
  place-items: center;
}

.brand-logo-image {
  width: 72px;
  height: 72px;
  object-fit: contain;
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.3));
}

.brand-logo-fallback {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #35c0ff, #6f6bff 50%, #a47dff);
  box-shadow: 0 12px 32px rgba(111, 107, 255, 0.3);
}

.logo-letter {
  font-size: 26px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 2px;
}

.brand-name {
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 800;
  color: #fff;
  margin: 0 0 12px;
  letter-spacing: 0.5px;
}

.brand-slogan {
  font-size: clamp(14px, 1.2vw, 17px);
  color: rgba(200, 218, 255, 0.8);
  margin: 0 0 8px;
  line-height: 1.5;
  font-weight: 400;
}

.brand-sub {
  font-size: 14px;
  color: rgba(180, 200, 255, 0.5);
  margin: 0;
  letter-spacing: 2px;
}

.brand-deco-line {
  position: absolute;
  bottom: 60px;
  left: 50%;
  transform: translateX(-50%);
  width: 120px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(111, 107, 255, 0.3), transparent);
}

/* ── Divider ── */

.split-divider {
  width: 1px;
  background: rgba(255, 255, 255, 0.1);
}

/* ── Right: Form Pane ── */

.form-pane {
  min-width: 0;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 32px 28px;
  background: #0a1234;
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
  background: linear-gradient(135deg, #35c0ff, #6f6bff 50%, #a47dff);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.22);
}

.logo-letter-sm {
  font-size: 16px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 1px;
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

/* ── Responsive ── */

@media (max-width: 1180px) {
  .auth-stage {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(200px, 32dvh) 1px 1fr;
  }

  .brand-pane {
    min-height: 200px;
  }

  .brand-logo-wrap {
    width: 52px;
    height: 52px;
    margin-bottom: 14px;
  }

  .brand-logo-image {
    width: 52px;
    height: 52px;
  }

  .brand-logo-fallback {
    width: 52px;
    height: 52px;
    border-radius: 16px;
  }

  .logo-letter {
    font-size: 20px;
  }

  .brand-name {
    font-size: 20px;
    margin-bottom: 6px;
  }

  .brand-slogan {
    font-size: 13px;
    margin-bottom: 4px;
  }

  .brand-sub {
    font-size: 12px;
  }

  .brand-deco-line {
    display: none;
  }

  .split-divider {
    width: 100%;
    height: 1px;
  }

  .form-pane {
    padding: 18px 14px 24px;
  }

  .form-panel-inner {
    width: min(100%, 560px);
    padding: 22px 18px 18px;
    border-radius: 18px;
  }
}
</style>
