/**
 * 图片压缩（上传前置处理）。
 *
 * 用 canvas 把最长边压到 `maxEdge` 以内、按 `quality` 重编码为 JPEG；
 * 无法解码（如 GIF / 已损坏）或已是小图时**原样返回**，绝不让上传失败。
 */
const MAX_EDGE = 1200
const QUALITY = 0.85

export async function compressImage(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif') return file
  try {
    const bitmap = await createImageBitmap(file)
    const longest = Math.max(bitmap.width, bitmap.height)
    if (longest <= MAX_EDGE && file.size <= 512 * 1024) {
      bitmap.close()
      return file
    }
    const scale = Math.min(1, MAX_EDGE / longest)
    const width = Math.max(1, Math.round(bitmap.width * scale))
    const height = Math.max(1, Math.round(bitmap.height * scale))

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      bitmap.close()
      return file
    }
    ctx.drawImage(bitmap, 0, 0, width, height)
    bitmap.close()

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob((b) => resolve(b), 'image/jpeg', QUALITY),
    )
    if (!blob || blob.size >= file.size) return file

    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], name, { type: 'image/jpeg' })
  } catch {
    // 解码失败：保底原样上传，不阻塞运营操作
    return file
  }
}
