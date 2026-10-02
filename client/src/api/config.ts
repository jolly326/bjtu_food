/// <reference types="vite/client" />

/**
 * 后端 API 基础路径。
 * 优先级：`VITE_API_BASE_URL`（.env.* / 命令行注入）> 本地联调默认值。
 * ⚠️ 必须含版本段 `/api/v1`，与后端 `server.servlet.context-path` 保持一致 —— 缺版本段 ⇒ 全站 404。
 */
const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL

// 兜底防线：生产构建若未注入 VITE_API_BASE_URL（如 .env.production 缺失 / 被清空），会静默回落到
// 本地默认基址（明文 http、指向 127.0.0.1）⇒ H5 生产请求不可达 / MP 路径前缀异常。此处显式告警便于定位。
if (import.meta.env.PROD && !import.meta.env.VITE_API_BASE_URL) {
  console.warn('[config] 未配置 VITE_API_BASE_URL，已回落到默认基址（仅本地联调可用）：', DEFAULT_API_BASE_URL)
}

/** 微信云托管环境 ID（callContainer 的 `config.env`） */
export const WX_CLOUD_ENV = 'prod-d7g2z0sge0919e273'

/** 微信云托管服务名（callContainer 请求头 `X-WX-SERVICE`） */
export const WX_SERVICE = 'bjtu-food'

/**
 * callContainer 路径前缀：**从 API_BASE_URL 推导，不得硬编码**。
 * 小程序走微信内部链路，请求域名由「环境 ID + X-WX-SERVICE」决定，故只需取 pathname。
 * 兜底：base 非法（URL 解析失败）时退回 `/api/v1`，与默认 base 的路径段一致。
 */
function resolveContainerPathPrefix(): string {
  try {
    const path = new URL(API_BASE_URL).pathname.replace(/\/+$/, '')
    return path || '/api/v1'
  } catch {
    return '/api/v1'
  }
}

/** callContainer 路径前缀（如 `/api/v1`），与 API_BASE_URL 的路径段同源 */
const WX_PATH_PREFIX = resolveContainerPathPrefix()

/** 拼接 callContainer 的最终 path（已带前缀则原样返回，避免重复拼接） */
export function buildContainerPath(url: string): string {
  if (!url) return WX_PATH_PREFIX
  if (url.startsWith(WX_PATH_PREFIX + '/') || url === WX_PATH_PREFIX) return url
  return `${WX_PATH_PREFIX}${url.startsWith('/') ? url : '/' + url}`
}
