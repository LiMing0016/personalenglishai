/**
 * 邮箱格式验证
 */
export function isValidEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/**
 * 密码强度验证（至少8位）
 */
export function isValidPassword(password: string): boolean {
  return password.length >= 8
}

/**
 * 统一错误消息处理（不暴露具体错误信息，防止邮箱枚举）
 */
export function getErrorMessage(code?: string, defaultMessage?: string): string {
  // 统一错误提示，不暴露具体错误原因
  return defaultMessage || '操作失败，请检查输入信息或稍后重试'
}


