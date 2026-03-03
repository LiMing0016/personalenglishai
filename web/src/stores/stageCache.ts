import { ref } from 'vue'

/**
 * Module-level cache for studyStage (avoids re-fetching on every navigation).
 * - null  = not yet fetched
 * - ''    = fetched but user has no stage
 * - other = the stage value
 */
export const stageCache = ref<string | null>(null)

export function clearStageCache() {
  stageCache.value = null
}
