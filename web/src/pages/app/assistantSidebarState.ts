export const ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH = 1280
export const DEFAULT_ASSISTANT_SIDEBAR_WIDTH = 218
export const MIN_ASSISTANT_SIDEBAR_WIDTH = 200
export const MAX_ASSISTANT_SIDEBAR_WIDTH = 360

export function clampAssistantSidebarWidth(width: number) {
  return Math.min(Math.max(width, MIN_ASSISTANT_SIDEBAR_WIDTH), MAX_ASSISTANT_SIDEBAR_WIDTH)
}

export function shouldAutoCollapseAssistantSidebar(options: {
  learningCanvasOpen: boolean
  viewportWidth: number
}) {
  return (
    options.learningCanvasOpen
    || options.viewportWidth <= ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH
  )
}
