export const AVATAR_MAX_SOURCE_BYTES = 5 * 1024 * 1024
export const AVATAR_MAX_OUTPUT_EDGE = 1024

const ALLOWED_AVATAR_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

export interface DecodedAvatarImage {
  source: CanvasImageSource
  width: number
  height: number
  release: () => void
}

export interface AvatarImageAdapter {
  decode(file: File): Promise<DecodedAvatarImage>
  encodePng(source: CanvasImageSource, width: number, height: number): Promise<Blob>
}

export function validateAvatarFile(file: File): string | null {
  if (!ALLOWED_AVATAR_TYPES.has(file.type.toLowerCase())) {
    return '请选择 JPG、PNG 或 WebP 图片'
  }
  if (file.size > AVATAR_MAX_SOURCE_BYTES) {
    return '头像不能超过 5MB'
  }
  return null
}

export function resolveAvatarOutputSize(
  width: number,
  height: number,
): { width: number; height: number } {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    throw new Error('invalid image dimensions')
  }

  const scale = Math.min(1, AVATAR_MAX_OUTPUT_EDGE / Math.max(width, height))
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

export async function normalizeAvatarFile(
  file: File,
  adapter: AvatarImageAdapter = browserAvatarImageAdapter,
): Promise<File> {
  const validationMessage = validateAvatarFile(file)
  if (validationMessage) {
    throw new Error(validationMessage)
  }

  let decoded: DecodedAvatarImage | null = null
  try {
    decoded = await adapter.decode(file)
    const outputSize = resolveAvatarOutputSize(decoded.width, decoded.height)
    const png = await adapter.encodePng(decoded.source, outputSize.width, outputSize.height)
    if (png.size > AVATAR_MAX_SOURCE_BYTES) {
      throw new Error('头像不能超过 5MB')
    }
    return new File([png], 'avatar.png', {
      type: 'image/png',
      lastModified: Date.now(),
    })
  } catch (error) {
    if (error instanceof Error && error.message === '头像不能超过 5MB') {
      throw error
    }
    throw new Error('图片处理失败，请重新选择')
  } finally {
    decoded?.release()
  }
}

const browserAvatarImageAdapter: AvatarImageAdapter = {
  async decode(file) {
    if (typeof createImageBitmap === 'function') {
      const bitmap = await createImageBitmap(file)
      return {
        source: bitmap,
        width: bitmap.width,
        height: bitmap.height,
        release: () => bitmap.close(),
      }
    }
    return decodeWithHtmlImage(file)
  },

  async encodePng(source, width, height) {
    if (typeof document === 'undefined') {
      throw new Error('canvas unavailable')
    }
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('canvas context unavailable')
    }
    context.drawImage(source, 0, 0, width, height)
    return new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (blob) {
          resolve(blob)
        } else {
          reject(new Error('png encoding failed'))
        }
      }, 'image/png')
    })
  },
}

async function decodeWithHtmlImage(file: File): Promise<DecodedAvatarImage> {
  if (typeof Image === 'undefined' || typeof URL.createObjectURL !== 'function') {
    throw new Error('image decoder unavailable')
  }

  const objectUrl = URL.createObjectURL(file)
  const image = new Image()
  try {
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve()
      image.onerror = () => reject(new Error('image decode failed'))
      image.src = objectUrl
    })
  } catch (error) {
    URL.revokeObjectURL(objectUrl)
    throw error
  }

  return {
    source: image,
    width: image.naturalWidth,
    height: image.naturalHeight,
    release: () => {
      image.src = ''
      URL.revokeObjectURL(objectUrl)
    },
  }
}
