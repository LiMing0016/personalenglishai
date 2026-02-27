/** 邮箱格式校验 */
export function isValidEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/** 密码强度校验（至少 8 位） */
export function isValidPassword(password: string): boolean {
  return password.length >= 8
}

/**
 * 统一错误消息处理（不暴露过多细节，避免账号枚举）
 * @param _code 预留字段，当前实现不按 code 分支
 */
export function getErrorMessage(_code?: string, defaultMessage?: string): string {
  return defaultMessage || '操作失败，请检查输入信息或稍后重试'
}
