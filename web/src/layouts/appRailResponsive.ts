export interface AppRailResponsiveInput {
  routePath: string
  narrowViewport: boolean
  personalCenterExpanded: boolean
  storedCollapsed: boolean
}

function isPersonalCenterPath(path: string) {
  return path === '/app/me' || path === '/dev/personal-center-preview'
}

export function resolveAppRailCollapsed(input: AppRailResponsiveInput) {
  if (input.narrowViewport && isPersonalCenterPath(input.routePath)) {
    return !input.personalCenterExpanded
  }
  return input.storedCollapsed
}
