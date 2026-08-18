import { API_BASE_URL } from '@/api/config'

/**
 * 将后端返回的图片路径转为可加载的绝对 URL。
 * - 已为 http(s)/data/blob 绝对地址或 /static/ 小程序本地资源：原样返回。
 * - /images/、/uploads/ 等后端相对路径：归一化掉可能已存在的 /api 前缀后，
 *   再拼 API_BASE_URL（其本身含 /api），避免双重 /api/api/uploads 前缀导致 404。
 *
 * 档口图片（P0 client-stall-img）根因说明：
 *   - 后端 `/canteens/all` 的 StallDetailVO.images 已是**后端拼好的绝对 URL 数组**
 *     （见 server CanteenService），前端透传即可；绝对 URL 一律原样返回，不会二次拼接。
 *   - 此处用 new URL() 解析做权威绝对地址判定兜底：凡可解析为绝对地址者（含 http(s)/data/blob）
 *     直接返回，彻底规避"绝对 URL 但路径段以 /images/、/uploads/ 开头"被误判为相对路径、
 *     导致双重前缀 /api/api 或错误改写绝对地址的边界问题。
 */
export function getImageUrl(path?: string | null): string {
  if (!path) return ''
  // 微信云存储文件 ID：<image> 组件原生支持 cloud:// 直接显示，原样返回
  if (path.startsWith('cloud://')) return path
  // 绝对地址兜底：data:/blob:/http(s): 以及任何可解析为 URL 的绝对地址，原样返回（档口图核心路径）
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  try {
    // 用 URL 解析做权威绝对判断：能解析成功即视为绝对地址（含 http(s)、// 协议相对等）
    // 注意：仅在 path 含协议时才构造成功，纯相对路径会抛错走下方归一化分支
    // eslint-disable-next-line no-new
    new URL(path)
    return path
  } catch {
    // 解析失败 = 相对路径，继续下方归一化
  }
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

/**
 * 推导图片缩略图路径：/images/.../xxx.jpg → /images/.../xxx_thumb.jpg（与后端 _thumb 命名严格一致）。
 * 仅对含 jpg/jpeg/png 扩展名的路径推导（webp 后端不生成缩略图，保持原路径）；已是 _thumb 或无法推导时原样返回。
 * 返回的是相对路径，调用方需再经 getImageUrl() 转绝对 URL。
 */
export function getThumbUrl(path?: string | null): string {
  if (!path) return ''
  // 微信云存储文件 ID：无缩略图概念，原样返回
  if (path.startsWith('cloud://')) return path
  const normalized = path.replace(/^\/api/, '')
  if (!/\.(jpg|jpeg|png)$/i.test(normalized)) return normalized
  if (/_thumb\./i.test(normalized)) return normalized
  return normalized.replace(/\.(jpg|jpeg|png)$/i, '_thumb.$1')
}

/** 将图片路径数组转为绝对 URL 并调起微信预览（统一 previewImage 实现，避免各组件重复 map） */
export function previewImages(images: string[], current = 0) {
  const urls = getImageUrls(images)
  uni.previewImage({ urls, current: urls[current] ?? urls[0] })
}
