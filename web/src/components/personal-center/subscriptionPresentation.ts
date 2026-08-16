import type { SubscriptionStatus } from '@/api/user'

export interface SubscriptionPresentation {
  planName: string
  periodText: string
  ariaLabel: string
}

export function buildSubscriptionPresentation(
  status: SubscriptionStatus | null,
  profileCreatedAt?: string | null,
): SubscriptionPresentation | null {
  if (!status) return null

  const start = isoDate(
    status.currentPeriodStart
      ?? (status.planCode === 'free' ? profileCreatedAt : null),
  )
  const end = status.planCode === 'free'
    ? '长期有效'
    : isoDate(status.currentPeriodEnd) ?? '结束时间待同步'
  const periodText = `${start ?? '生效时间待同步'} — ${end}`

  return {
    planName: status.planName,
    periodText,
    ariaLabel: `当前档位 ${status.planName}，有效期 ${periodText}`,
  }
}

function isoDate(value?: string | null): string | null {
  return value?.match(/^\d{4}-\d{2}-\d{2}/)?.[0] ?? null
}
