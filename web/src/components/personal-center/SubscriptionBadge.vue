<template>
  <span v-if="presentation" class="subscription-badge-wrap">
    <button
      class="subscription-badge-button"
      type="button"
      :aria-label="presentation.ariaLabel"
      :aria-describedby="tooltipId"
      @keydown.escape="blurBadge"
    >
      {{ presentation.planName }}
    </button>
    <span :id="tooltipId" class="subscription-popover" role="tooltip">
      <span class="popover-kicker">当前档位</span>
      <strong>{{ presentation.planName }}</strong>
      <span class="popover-divider"></span>
      <span class="popover-kicker">有效期</span>
      <span class="popover-period">{{ presentation.periodText }}</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { SubscriptionStatus } from '@/api/user'
import { buildSubscriptionPresentation } from './subscriptionPresentation'

const props = defineProps<{
  status: SubscriptionStatus | null
  profileCreatedAt?: string | null
}>()

const tooltipId = 'personal-subscription-details'
const presentation = computed(() => buildSubscriptionPresentation(
  props.status,
  props.profileCreatedAt,
))

function blurBadge(event: KeyboardEvent) {
  (event.currentTarget as HTMLButtonElement).blur()
}
</script>

<style scoped>
.subscription-badge-wrap {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.subscription-badge-button {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  border: 1px solid rgba(4, 120, 87, 0.2);
  border-radius: 999px;
  padding: 2px 9px;
  background: rgba(226, 245, 238, 0.82);
  color: #047857;
  cursor: pointer;
  font-size: 11px;
  font-weight: 760;
  letter-spacing: 0.02em;
  line-height: 1;
  transition:
    border-color 160ms ease,
    background 160ms ease,
    box-shadow 160ms ease;
}

.subscription-badge-button:hover,
.subscription-badge-button:focus-visible {
  border-color: rgba(4, 120, 87, 0.42);
  background: #e7f7f1;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.09);
  outline: none;
}

.subscription-popover {
  position: absolute;
  z-index: 40;
  top: calc(100% + 9px);
  left: 50%;
  display: grid;
  width: max-content;
  min-width: 218px;
  max-width: min(280px, calc(100vw - 32px));
  gap: 3px;
  border: 1px solid #dce7e2;
  border-radius: 12px;
  padding: 13px 14px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 16px 36px rgba(22, 53, 45, 0.16);
  color: #10243f;
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, -4px);
  transition:
    opacity 140ms ease,
    transform 140ms ease,
    visibility 140ms ease;
  visibility: hidden;
}

.subscription-popover::before {
  position: absolute;
  top: -5px;
  left: 50%;
  width: 9px;
  height: 9px;
  border-top: 1px solid #dce7e2;
  border-left: 1px solid #dce7e2;
  background: #fff;
  content: '';
  transform: translateX(-50%) rotate(45deg);
}

.subscription-badge-wrap:hover .subscription-popover,
.subscription-badge-wrap:focus-within .subscription-popover {
  opacity: 1;
  transform: translate(-50%, 0);
  visibility: visible;
}

.popover-kicker {
  color: #789087;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.subscription-popover strong {
  color: #087a59;
  font-size: 15px;
}

.popover-divider {
  height: 1px;
  margin: 7px 0 5px;
  background: #edf2ef;
}

.popover-period {
  color: #4f6573;
  font-size: 12px;
  line-height: 1.55;
  white-space: nowrap;
}

@media (max-width: 480px) {
  .subscription-popover {
    left: auto;
    right: -38px;
    transform: translateY(-4px);
  }

  .subscription-popover::before {
    right: 52px;
    left: auto;
    transform: rotate(45deg);
  }

  .subscription-badge-wrap:hover .subscription-popover,
  .subscription-badge-wrap:focus-within .subscription-popover {
    transform: translateY(0);
  }
}
</style>
