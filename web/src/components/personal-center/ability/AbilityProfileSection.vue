<template>
  <AbilityOverview
    v-if="selectedModule === null"
    :model="overviewModel"
    :loading="overviewLoading"
    :error="overviewError"
    @open-module="emit('open-module', $event)"
    @retry="retryProfile"
  />
  <AbilityModuleDetail
    v-else-if="detail"
    :detail="detail"
    :loading="detailLoading"
    :error="detailError"
    @back="emit('close-module')"
    @retry="retryWritingDetail"
  />
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue'

import AbilityOverview from './AbilityOverview.vue'
import AbilityModuleDetail from './AbilityModuleDetail.vue'
import {
  buildAbilityOverviewModel,
  buildUnavailableAbilityDetail,
  buildWritingAbilityDetail,
  type AbilityModuleKey,
} from './abilityProfileModel'
import { usePersonalAbilityData } from './usePersonalAbilityData'

const props = defineProps<{
  selectedModule: AbilityModuleKey | null
  previewMode: boolean
}>()

const emit = defineEmits<{
  'open-module': [key: AbilityModuleKey]
  'close-module': []
}>()

const previewMode = toRef(props, 'previewMode')
const selectedModule = toRef(props, 'selectedModule')
const {
  profile,
  writingDashboard,
  writingStats,
  profileQuery,
  writingDashboardQuery,
  writingStatsQuery,
} = usePersonalAbilityData(previewMode, selectedModule)

const overviewModel = computed(() => buildAbilityOverviewModel(profile.value))
const overviewLoading = computed(() => !previewMode.value && profileQuery.isPending.value)
const overviewError = computed(() => !previewMode.value && profileQuery.isError.value)
const detail = computed(() => {
  const key = props.selectedModule
  if (key === 'writing') {
    return buildWritingAbilityDetail(
      profile.value,
      writingDashboard.value,
      writingStats.value,
    )
  }
  return key ? buildUnavailableAbilityDetail(key) : null
})
const detailLoading = computed(() => (
  props.selectedModule === 'writing'
  && !previewMode.value
  && (writingDashboardQuery.isPending.value || writingStatsQuery.isPending.value)
))
const detailError = computed(() => (
  props.selectedModule === 'writing'
  && !previewMode.value
  && (writingDashboardQuery.isError.value || writingStatsQuery.isError.value)
))

function retryProfile() {
  void profileQuery.refetch()
}

function retryWritingDetail() {
  if (writingDashboardQuery.isError.value) void writingDashboardQuery.refetch()
  if (writingStatsQuery.isError.value) void writingStatsQuery.refetch()
}
</script>
