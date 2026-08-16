import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'

import { userApi } from '@/api/user'
import { getWritingDashboard, getWritingStats } from '@/api/writing'
import type { AbilityModuleKey } from './abilityProfileModel'
import {
  PREVIEW_ABILITY_PROFILE,
  PREVIEW_WRITING_DASHBOARD,
  PREVIEW_WRITING_STATS,
} from './abilityProfilePreview'

export function usePersonalAbilityData(
  previewMode: Ref<boolean>,
  selectedModule: Ref<AbilityModuleKey | null>,
) {
  const profileQuery = useQuery({
    queryKey: ['personal-center', 'ability', 'profile'],
    queryFn: async () => (await userApi.getAbilityProfile()).data ?? null,
    enabled: computed(() => !previewMode.value),
    staleTime: 60_000,
  })

  const writingEnabled = computed(
    () => !previewMode.value && selectedModule.value === 'writing',
  )
  const writingDashboardQuery = useQuery({
    queryKey: ['personal-center', 'ability', 'writing-dashboard', 'all'],
    queryFn: () => getWritingDashboard({ range: 'all', mode: 'all' }),
    enabled: writingEnabled,
    staleTime: 60_000,
  })
  const writingStatsQuery = useQuery({
    queryKey: ['personal-center', 'ability', 'writing-stats'],
    queryFn: getWritingStats,
    enabled: writingEnabled,
    staleTime: 60_000,
  })

  return {
    profile: computed(() => previewMode.value ? PREVIEW_ABILITY_PROFILE : profileQuery.data.value ?? null),
    writingDashboard: computed(() => previewMode.value ? PREVIEW_WRITING_DASHBOARD : writingDashboardQuery.data.value ?? null),
    writingStats: computed(() => previewMode.value ? PREVIEW_WRITING_STATS : writingStatsQuery.data.value ?? null),
    profileQuery,
    writingDashboardQuery,
    writingStatsQuery,
  }
}
