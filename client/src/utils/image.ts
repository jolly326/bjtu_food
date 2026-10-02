import { API_BASE_URL } from '@/api/config'

/**
 * 后端图片路径 → 可加载的绝对 URL。
 * - `cloud://`（云存储）/ http(s) / data: / blob: / `/static/` → 原样返回；
 * - `/images/`、`/uploads/` 等相对路径 → 去掉可能已存在的 `/api` 前缀后拼 `API_BASE_URL`
 *   （该常量本身含 `/api/v1`，不去重会出现 /api/api 双重前缀 → 404）。
 *
 * 绝对地址判定用 `new URL()` 兜底：可解析即视为绝对地址，避免「绝对 URL 但路径段像相对路径」被误改写。
 */
export function getImageUrl(path?: string | null): string {
  if (!path) return ''
  // 云存储文件 ID：<image> 原生支持 cloud://，原样返回
  if (path.startsWith('cloud://')) return path
  // 显式绝对地址 / 伪协议
  if (/^(https?:|data:|blob:)/i.test(path)) return path
  try {
    // 能解析成功即视为绝对地址（纯相对路径会抛错，走下方归一化分支）
    // eslint-disable-next-line no-new
    new URL(path)
    return path
  } catch {
    // 解析失败 = 相对路径，继续归一化
  }
  if (path.startsWith('/static/')) return path
  if (path.startsWith('/images/') || path.startsWith('/uploads/')) {
    const normalized = path.replace(/^\/api/, '')
    return `${API_BASE_URL}${normalized}`
  }
  return path
}

/**
 * 缩略图 URL 推导（列表 / 卡片专用；详情大图仍用 {@link getImageUrl} 原图）。
 *
 * 两条链路必须区分（否则会出现「首页白图、详情正常」的不一致）：
 * 1. **COS 绝对 URL（当前生产链路）**：COS 上不存在 `_thumb` 变体文件，追加图片处理参数
 *    `?imageMogr2/thumbnail/400x` 生成真缩略图（仅对 `*.myqcloud.com` 域名追加）。
 * 2. **后端本地磁盘相对路径（`/images/...`，本地开发）**：按 `_thumb` 命名推导
 *    （后端 ImageIO 生成的 `{base}_thumb.{ext}` 同目录文件）。
 *
 * `cloud://`、data:/blob:、无图片扩展名、已含处理参数一律原样返回。
 */
function getThumbUrl(path?: string | null): string {
  if (!path) return ''
  if (path.startsWith('cloud://')) return path

  const normalized = path.replace(/^\/api/, '')

  // 伪协议不可追加处理参数
  if (/^(data:|blob:)/i.test(normalized)) return normalized

  // ---- 链路 1：COS 绝对 URL ----
  if (/^https?:\/\//i.test(normalized)) {
    const withoutQuery = normalized.split('?')[0]
    if (!/\.(jpg|jpeg|png)$/i.test(withoutQuery)) return normalized
    if (/[?&]imageMogr2=/i.test(normalized)) return normalized
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

/**
 * 列表 / 卡片的最终图片地址 = **缩略图推导 + 绝对化**，一次到位。
 * 禁止调用点手写 `getImageUrl(getThumbUrl(x))`（顺序写反或漏写会导致「有的地方走缩略图、有的走原图」）。
 * 空值 / 空串一律返回 `''`，调用方据此走占位图。
 */
export function getThumbImageUrl(path?: string | null): string {
  if (!path) return ''
  return getImageUrl(getThumbUrl(path))
}
