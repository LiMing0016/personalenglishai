<template>
  <main class="home-shell">
    <section class="home-hero" aria-labelledby="home-title">
      <div class="brand-mark">
        <img src="/brand/peai-logo.png" alt="PEAI" />
        <span>Personal English AI</span>
      </div>

      <div class="hero-copy">
        <p class="eyebrow">英语学习工作台</p>
        <h1 id="home-title">Personal English AI</h1>
        <p class="subtitle">写作、词汇、听说练习与 AI 助手集中在一个学习端。</p>
      </div>

      <div class="hero-actions" aria-label="学习端入口">
        <router-link to="/app" class="primary-action">进入学习</router-link>
        <router-link to="/login" class="secondary-action">登录</router-link>
        <router-link to="/register" class="text-action">注册账号</router-link>
      </div>
    </section>

    <section class="entry-band" aria-label="内部入口">
      <span>内部运营与权限管理</span>
      <router-link to="/admin" class="internal-link">管理员入口</router-link>
    </section>
  </main>
</template>

<script setup lang="ts">
// Public entry page
</script>

<style scoped>
.home-shell {
  min-height: 100vh;
  background:
    linear-gradient(180deg, rgba(248, 252, 247, 0.96), rgba(242, 247, 244, 0.96)),
    #f6faf7;
  color: #10231f;
  display: flex;
  flex-direction: column;
}

.home-hero {
  width: min(1080px, calc(100% - 40px));
  min-height: 78vh;
  margin: 0 auto;
  padding: 56px 0 36px;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 44px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #415850;
  font-size: 15px;
  font-weight: 700;
}

.brand-mark img {
  width: 42px;
  height: 42px;
  border-radius: 10px;
}

.hero-copy {
  align-self: center;
  max-width: 720px;
}

.eyebrow {
  margin: 0 0 14px;
  color: #0f7a55;
  font-size: 15px;
  font-weight: 700;
}

.hero-copy h1 {
  margin: 0;
  color: #0d221c;
  font-size: clamp(42px, 7vw, 76px);
  line-height: 1.03;
  letter-spacing: 0;
}

.subtitle {
  max-width: 620px;
  margin: 22px 0 0;
  color: #536960;
  font-size: 20px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.primary-action,
.secondary-action,
.text-action,
.internal-link {
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 700;
  text-decoration: none;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.primary-action {
  padding: 0 26px;
  background: #08754f;
  color: #fff;
}

.primary-action:hover {
  background: #056543;
}

.secondary-action {
  padding: 0 24px;
  border: 1px solid #b8cdc3;
  background: #fff;
  color: #113c30;
}

.secondary-action:hover {
  border-color: #7fa996;
  background: #f6fbf8;
}

.text-action {
  padding: 0 10px;
  color: #496259;
}

.text-action:hover {
  color: #08754f;
}

.entry-band {
  width: 100%;
  min-height: 84px;
  padding: 18px max(20px, calc((100vw - 1080px) / 2));
  border-top: 1px solid #dbe8df;
  background: rgba(255, 255, 255, 0.68);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  color: #6a7a74;
  font-size: 14px;
}

.internal-link {
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid #d1ded6;
  background: #f7faf8;
  color: #36554a;
  font-size: 14px;
}

.internal-link:hover {
  border-color: #9fb8ad;
  color: #08754f;
}

@media (max-width: 640px) {
  .home-hero {
    width: min(100% - 28px, 1080px);
    min-height: 76vh;
    padding-top: 32px;
    gap: 32px;
  }

  .brand-mark span {
    font-size: 14px;
  }

  .hero-copy h1 {
    font-size: 42px;
  }

  .subtitle {
    font-size: 17px;
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .primary-action,
  .secondary-action,
  .text-action {
    width: 100%;
  }

  .entry-band {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
