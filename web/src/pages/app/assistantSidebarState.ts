export const ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH = 1280

export function shouldAutoCollapseAssistantSidebar(options: {
  learningCanvasOpen: boolean
  viewportWidth: number
}) {
  return (
    options.learningCanvasOpen
    || options.viewportWidth <= ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH
  )
}
