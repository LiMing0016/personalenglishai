function imageExtension(type: string) {
  if (type === 'image/jpeg') return '.jpg'
  if (type === 'image/webp') return '.webp'
  return '.png'
}

function isImageFile(file: File, itemType = '') {
  return file.type.startsWith('image/') || itemType.startsWith('image/')
}

export function extractImageFilesFromClipboardData(clipboardData: DataTransfer | null): File[] {
  if (!clipboardData) {
    return []
  }

  const itemFiles = Array.from(clipboardData.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item, index) => {
      const file = item.getAsFile()
      if (!file || !isImageFile(file, item.type)) return null
      return new File([file], `pasted-image-${Date.now()}-${index}${imageExtension(file.type || item.type)}`, {
        type: file.type || item.type || 'image/png',
      })
    })
    .filter((file): file is File => Boolean(file))

  if (itemFiles.length > 0) {
    return itemFiles
  }

  return Array.from(clipboardData.files ?? []).filter((file) => isImageFile(file))
}
