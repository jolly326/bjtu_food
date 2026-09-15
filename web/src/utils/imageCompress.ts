/**
 * 图片前端压缩（§7.17：管理后台上传手机原图时自动压缩，规避后端 5MB 限制）。
 *
 * 触发门槛（不满足则原图直传，避免无谓画质损失与耗时）：
 *   - 文件 > 1MB，或
 *   - 宽度 > 1200px
 * 参数：
 *   - 最大宽度 1200px（等比，不放大）；
 *   - 原图 image/png 输出 PNG（保留透明），其余输出 JPEG；
 *   - JPEG 质量 0.85 → 0.75 → 0.65 → 0.6 逐级下调，直到 ≤1MB 或触底 0.6。
 *
 * EXIF 方向：手机竖拍照片携带 Orientation 元数据，普通 <img>/drawImage 重绘会丢方向导致横置。
 * 优先使用 createImageBitmap(blob, { imageOrientation: 'from-image' })，由浏览器解码阶段即应用方向；
 * 不支持时（如 Firefox 旧版本、部分 Safari）回退 createImageBitmap(blob) / HTMLImageElement（小图可零成本读像素）。
 *
 * 降级：canvas/压缩链路任一步异常 → 返回原文件（保证上传可用），仅控制台 warn。
 */

// 以下三个阈值仅本文件内消费（外部出口为 compressImage / CompressResult），故不导出。
const COMPRESS_THRESHOLD_BYTES = 1 * 1024 * 1024 // 1MB
const COMPRESS_MAX_EDGE = 1200
const COMPRESS_UPLOAD_LIMIT_BYTES = 5 * 1024 * 1024 // 后端单图 5MB 上限
const JPEG_QUALITIES = [0.85, 0.75, 0.65, 0.6]
const MIN_QUALITY = 0.6

export interface CompressResult {
  file: File
  /** 实际输出的字节数（回退时为原文件大小） */
  size: number
  /** 是否仍在后端 5MB 上限内 */
  withinLimit: boolean
}

function isPng(file: File): boolean {
  return file.type === 'image/png'
}

/** 读取图片原始像素尺寸（用于触发条件判定）；解码失败返回 null 走直传 */
async function readDimensions(bitmap: ImageBitmap | HTMLImageElement): Promise<{ width: number; height: number }> {
  return { width: bitmap.width, height: bitmap.height }
}

/** 解码为可绘制源：优先带方向信息的 ImageBitmap，失败回退 HTMLImageElement */
async function decode(file: File): Promise<{ source: CanvasImageSource; width: number; height: number } | null> {
  const createBitmap = (window as any).createImageBitmap as
    | ((blob: Blob, options?: ImageBitmapOptions) => Promise<ImageBitmap>)
    | undefined
  if (typeof createBitmap === 'function') {
    try {
      const bitmap = await createBitmap(file, { imageOrientation: 'from-image' })
      const dim = await readDimensions(bitmap)
      if (dim.width && dim.height) return { source: bitmap, width: dim.width, height: dim.height }
    } catch (err) {
      console.warn('[imageCompress] createImageBitmap(from-image) 失败，回退默认解码', err)
    }
    try {
      const bitmap = await createBitmap(file)
      const dim = await readDimensions(bitmap)
      if (dim.width && dim.height) return { source: bitmap, width: dim.width, height: dim.height }
    } catch (err) {
      console.warn('[imageCompress] createImageBitmap 失败，回退 HTMLImageElement', err)
    }
  }
  try {
    const url = URL.createObjectURL(file)
    try {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const el = new Image()
        el.onload = () => resolve(el)
        el.onerror = () => reject(new Error('图片解码失败'))
        el.src = url
      })
      return { source: img, width: img.naturalWidth, height: img.naturalHeight }
    } finally {
      URL.revokeObjectURL(url)
    }
  } catch (err) {
    console.warn('[imageCompress] HTMLImageElement 解码失败，跳过压缩直传原图', err)
    return null
  }
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('canvas.toBlob 返回空'))),
      type,
      quality,
    )
  })
}

function withSuffix(name: string, ext: string): string {
  const base = name.replace(/\.[^./\\]+$/, '') || 'image'
  return `${base}.${ext}`
}

/**
 * 需要压缩才压缩；否则返回原文件。
 * 绝不抛异常：任何异常均降级为原文件直传。
 */
export async function compressImageIfNeeded(file: File): Promise<CompressResult> {
  const fallback: CompressResult = { file, size: file.size, withinLimit: file.size <= COMPRESS_UPLOAD_LIMIT_BYTES }

  const png = isPng(file)
  const needsCompressBySize = file.size > COMPRESS_THRESHOLD_BYTES

  try {
    const decoded = await decode(file)
    // 解码失败：无法判定宽高，按体积决定是否需要尝试；此处直接直传（保守可用优先）
    if (!decoded) return fallback

    const needsCompressByWidth = decoded.width > COMPRESS_MAX_EDGE
    if (!needsCompressBySize && !needsCompressByWidth) return fallback

    const scale = decoded.width > COMPRESS_MAX_EDGE ? COMPRESS_MAX_EDGE / decoded.width : 1
    const targetWidth = Math.max(1, Math.round(decoded.width * scale))
    const targetHeight = Math.max(1, Math.round(decoded.height * scale))

    const canvas = document.createElement('canvas')
    canvas.width = targetWidth
    canvas.height = targetHeight
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      console.warn('[imageCompress] 无法获取 canvas 2d 上下文，跳过压缩直传原图')
      return fallback
    }
    // PNG 需要透明底：不清空为白底，直接绘制
    ctx.drawImage(decoded.source as CanvasImageSource, 0, 0, targetWidth, targetHeight)

    const outType = png ? 'image/png' : 'image/jpeg'
    let blob: Blob | null = null

    if (png) {
      // PNG 无损，无质量档位；仍超阈值时无法通过降质量继续压缩，按现状返回
      blob = await canvasToBlob(canvas, outType)
    } else {
      for (const quality of JPEG_QUALITIES) {
        const candidate = await canvasToBlob(canvas, outType, quality)
        blob = candidate
        if (candidate.size <= COMPRESS_THRESHOLD_BYTES) break
        if (quality === MIN_QUALITY) break
      }
    }

    if (!blob) return fallback

    // 压缩后反而更大（小体积原图/PNG 场景常见）→ 保留原文件
    if (blob.size >= file.size && file.size <= COMPRESS_UPLOAD_LIMIT_BYTES) return fallback

    const ext = png ? 'png' : 'jpg'
    const compressed = new File([blob], withSuffix(file.name, ext), { type: outType, lastModified: Date.now() })
    return { file: compressed, size: compressed.size, withinLimit: compressed.size <= COMPRESS_UPLOAD_LIMIT_BYTES }
  } catch (err) {
    console.warn('[imageCompress] 压缩异常，回退原图直传', err)
    return fallback
  }
}
