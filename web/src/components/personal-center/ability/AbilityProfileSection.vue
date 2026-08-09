<template>
  <AbilityOverview
    v-if="selectedModule === null"
    :model="overviewModel"
    :loading="overviewLoading"
    :error="overviewError"
    @open-module="emit('open-module', $event)"
    @retry="retryProfile"
  />
</template>

<script setup lang="ts">
import { computed, toRef } from 'vue'

import AbilityOverview from './AbilityOverview.vue'
import {
  buildAbilityOverviewModel,
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
const { profile, profileQuery } = usePersonalAbilityData(previewMode, selectedModule)

const overviewModel = computed(() => buildAbilityOverviewModel(profile.value))
const overviewLoading = computed(() => !previewMode.value && profileQuery.isPending.value)
const overviewError = computed(() => !previewMode.value && profileQuery.isError.value)

function retryProfile() {
  void profileQuery.refetch()
}
</script>
