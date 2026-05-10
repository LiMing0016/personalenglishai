/** 邮箱格式校验 */
export function isValidEmail(email: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/** 密码强度校验（至少 8 位，包含大小写字母和数字） */
export function isValidPassword(password: string): boolean {
  return (
    password.length >= 8 &&
    /[a-z]/.test(password) &&
    /[A-Z]/.test(password) &&
    /[0-9]/.test(password)
  )
}

/** 中国大陆手机号校验（1[3-9] 开头 11 位） */
export function isValidPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

/** 短信验证码校验（6 位数字） */
export function isValidSmsCode(code: string): boolean {
  return /^\d{6}$/.test(code)
}

/**
 * 统一错误消息处理（不暴露过多细节，避免账号枚举）
 */
export function getErrorMessage(code?: string, defaultMessage?: string): string {
  if (code === '403020') {
    return '邮箱尚未验证，请先完成邮箱验证'
  }
  if (code === '429003') {
    return '验证邮件发送过于频繁，请稍后再试'
  }
  return defaultMessage || '操作失败，请检查输入信息或稍后重试'
}
