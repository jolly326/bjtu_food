import { API_BASE_URL } from '@/api/config'

/**
 * 将后端返回的图片路径转为可加载的绝对 URL。
 * - 已为 http(s)/data/blob 绝对地址或 /static/ 小程序本地资源：原样返回。
 * - /images/、/uploads/ 等后端相对路径：归一化掉可能已存在的 /api 前缀后，
 *   再拼 API_BASE_URL（其本身含 /api），避免双重 /api/api/uploads 前缀导致 404。
 *
 * 档口图片（P0 client-stall-img）根因说明：
 *   - 后端食堂 / 档口字典出参自 2026-09-21（§7.33）已收敛为 id / name，**不再返回 images**；
 *     本条保留的是「绝对 URL 一律原样返回、不二次拼接」的判定口径（仍适用于菜品图等绝对地址）。
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

/**
 * 缩略图 URL 推导（列表/卡片专用；详情大图仍用 getImageUrl 原图）。
 *
 * 两条链路必须区分开（C15 修复：首页与详情显示不一致的根因）：
 * 1. **云对象存储绝对 URL（COS，当前生产链路）**：COS 上**不存在** `_thumb` 变体文件，
 *    沿用旧约定改写会 404 → 列表白图（首页、搜索结果），而详情页用原图正常。
 *    改为追加 **COS 图片处理参数** 生成真缩略图：`?imageMogr2/thumbnail/400x`
 *    （实测：原图 166KB → 缩略图 37KB，且清晰度对列表卡片足够）。
 * 2. **后端本地磁盘相对路径（`/images/...`，本地开发/历史数据）**：仍按 `_thumb` 命名推导
 *    （后端 ImageIO 生成的 `{base}_thumb.{ext}` 同目录文件）。
 *
 * 其它情况（`cloud://` 云存储、data:/blob:、无图片扩展名、已含处理参数）一律原样返回。
 */
export function getThumbUrl(path?: string | null): string {
  if (!path) return ''
  // 微信云存储文件 ID：无缩略图概念，原样返回
  if (path.startsWith('cloud://')) return path

  const normalized = path.replace(/^\/api/, '')

  // 非 http(s) 的伪协议（data:/blob:）不可追加处理参数
  if (/^(data:|blob:)/i.test(normalized)) return normalized

  // ---- 链路 1：云存储绝对 URL（COS）----
  if (/^https?:\/\//i.test(normalized)) {
    const withoutQuery = normalized.split('?')[0]
    if (!/\.(jpg|jpeg|png)$/i.test(withoutQuery)) return normalized
    // 已带图片处理参数：不重复追加
    if (/[?&]imageMogr2=/i.test(normalized)) return normalized
    // 仅对腾讯云 COS 域名（*.myqcloud.com）追加，避免污染第三方图源
    const host = (normalized.match(/^https?:\/\/([^/?#]+)/i) || [])[1] || ''
    if (!/\.myqcloud\.com$/i.test(host)) return normalized
    const sep = normalized.includes('?') ? '&' : '?'
    return `${normalized}${sep}imageMogr2/thumbnail/400x`
  }

  // ---- 链路 2：后端本地磁盘相对路径 ----
  if (!/\.(jpg|jpeg|png)$/i.test(normalized)) return normalized
  if (/_thumb\./i.test(normalized)) return normalized
  return normalized.replace(/\.(jpg|jpeg|png)$/i, '_thumb.$1')
}
