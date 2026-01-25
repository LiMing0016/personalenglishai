/**
 * 简单的 Toast 提示（生产环境建议使用 toast 库）
 */
let toastContainer: HTMLDivElement | null = null

function ensureToastContainer() {
  if (!toastContainer) {
    toastContainer = document.createElement('div')
    toastContainer.id = 'toast-container'
    toastContainer.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 10000;
      pointer-events: none;
    `
    document.body.appendChild(toastContainer)
  }
}

export function showToast(message: string, type: 'success' | 'error' | 'info' = 'info') {
  ensureToastContainer()

  const toast = document.createElement('div')
  const bgColor = type === 'success' ? '#4caf50' : type === 'error' ? '#f44336' : '#2196f3'
  
  toast.style.cssText = `
    background: ${bgColor};
    color: white;
    padding: 12px 24px;
    border-radius: 4px;
    margin-bottom: 10px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.2);
    pointer-events: auto;
    animation: slideIn 0.3s ease-out;
  `

  toast.textContent = message
  toastContainer!.appendChild(toast)

  setTimeout(() => {
    toast.style.animation = 'slideOut 0.3s ease-out'
    setTimeout(() => {
      toast.remove()
    }, 300)
  }, 3000)
}

// 添加动画样式
if (typeof document !== 'undefined') {
  const style = document.createElement('style')
  style.textContent = `
    @keyframes slideIn {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    @keyframes slideOut {
      from {
        transform: translateX(0);
        opacity: 1;
      }
      to {
        transform: translateX(100%);
        opacity: 0;
      }
    }
  `
  document.head.appendChild(style)
}


