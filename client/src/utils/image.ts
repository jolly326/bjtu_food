import { API_BASE_URL } from '@/api/config'

export function getImageUrl(path?: string | null): string {
  if (!path) return ''
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  if (path.startsWith('/static/')) return path
  if (path.startsWith('/images/') || path.startsWith('/uploads/')) return `${API_BASE_URL}${path}`
  return path
}

/** 将图片路径数组转为绝对 URL 并调起微信预览（统一 previewImage 实现，避免各组件重复 map） */
export function previewImages(images: string[], current = 0) {
  const urls = images.map(getImageUrl)
  uni.previewImage({ urls, current: urls[current] ?? urls[0] })
}
