import { API_BASE_URL } from '@/api/config'

/**
 * 将后端返回的图片路径转为可加载的绝对 URL。
 * - 已为 http(s)/data/blob 绝对地址或 /static/ 小程序本地资源：原样返回。
 * - /images/、/uploads/ 等后端相对路径：归一化掉可能已存在的 /api 前缀后，
 *   再拼 API_BASE_URL（其本身含 /api），避免双重 /api/api/uploads 前缀导致 404。
 */
export function getImageUrl(path?: string | null): string {
  if (!path) return ''
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  if (path.startsWith('/static/')) return path
  if (path.startsWith('/images/') || path.startsWith('/uploads/')) {
    const normalized = path.replace(/^\/api/, '')
    return `${API_BASE_URL}${normalized}`
  }
  return path
}

/** 批量转换图片路径数组（保持顺序），供组件图片网格 / 头像列表复用 */
export function getImageUrls(images?: (string | null)[] | null): string[] {
  if (!images || !Array.isArray(images)) return []
  return images.map((img) => getImageUrl(img)).filter(Boolean)
}

/** 将图片路径数组转为绝对 URL 并调起微信预览（统一 previewImage 实现，避免各组件重复 map） */
export function previewImages(images: string[], current = 0) {
  const urls = getImageUrls(images)
  uni.previewImage({ urls, current: urls[current] ?? urls[0] })
}
