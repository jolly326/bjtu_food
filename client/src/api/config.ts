/// <reference types="vite/client" />

/**
 * 后端 API 基础路径配置
 *
 * 读取优先级（从高到低）：
 * 1. 环境变量 VITE_API_BASE_URL（可在 .env.development / .env.production 或命令行注入）
 * 2. 下方 DEFAULT_API_BASE_URL（本地联调默认 127.0.0.1:8080）
 *
 * ⚠️ base **必须含版本段 /api/v1**，与后端 application.yml 的
 *    `server.servlet.context-path = /api/v1` 一致；缺版本段 → 全站 404。
 *
 * 用法示例：
 * - 真机预览（手机与电脑同一 WiFi）：VITE_API_BASE_URL=http://<电脑局域网IP>:8080/api/v1
 * - 部署上线：VITE_API_BASE_URL=https://<你的域名>/api/v1
 */
const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'

export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL

/**
 * 微信云托管环境 ID（小程序端 wx.cloud.callContainer 使用）
 * 对应：微信云托管环境 prod-d7g2z0sge0919e273
 */
export const WX_CLOUD_ENV = 'prod-d7g2z0sge0919e273'

/**
 * 微信云托管服务名（callContainer 请求头 X-WX-SERVICE）
 * 对应：云托管服务 bjtu-food
 */
export const WX_SERVICE = 'bjtu-food'

/**
 * callContainer 请求路径前缀（**从 API_BASE_URL 推导，不再硬编码 `/api`**）。
 *
 * <p><b>为何必须推导而非写死</b>：原实现是
 * {@code path: url.startsWith('/api') ? url : `/api${url}`}，即小程序分支**自行拼 `/api` 前缀**、
 * 完全不读 {@code API_BASE_URL}。而后端 {@code context-path} 在 已由
 * {@code /api} 升级为 {@code /api/v1}，于是：
 *
 * <pre>
 *   H5 分支：  uni.request(`${API_BASE_URL}${url}`) → /api/v1/dishes  ✅
 *   小程序：   callContainer(`/api${url}`)           → /api/dishes      ❌ 缺 v1 → 全站 404
 * </pre>
 *
 * <p>该缺陷隐蔽的原因：① 只在真机/开发者工具暴露（H5 走另一分支且正常）；
 * ② 404 发生在云托管网关层，**后端日志收不到该请求**，翻服务端日志也看不出。
 * 而 {@code VITE_API_BASE_URL} 里明明已配好 {@code /api/v1}，只是这条分支没读它——
 * 「配置正确但未被使用」是最容易骗过人的形态。
 *
 * <p><b>取 pathname 而非 host</b>：callContainer 走微信内部链路，
 * 请求域名由「环境 ID + X-WX-SERVICE」确定，故只需路径部分。
 *
 * <p><b>兜底</b>：{@link URL} 解析失败（非法 base）时退回 {@code /api/v1}，
 * 与 {@link DEFAULT_API_BASE_URL} 的路径段保持一致，不让构建期变量写错就崩溃。
 */
function resolveContainerPathPrefix(): string {
  try {
    const path = new URL(API_BASE_URL).pathname.replace(/\/+$/, '')
    return path || '/api/v1'
  } catch {
    return '/api/v1'
  }
}

/** callContainer 路径前缀（如 `/api/v1`）；与 {@link API_BASE_URL} 的路径段恒同源 */
export const WX_PATH_PREFIX = resolveContainerPathPrefix()

/**
 * 拼接 callContainer 的最终 path。
 * <p>避免重复拼接：若调用方传入的 url 已带前缀（历史遗留写法），直接返回。
 */
export function buildContainerPath(url: string): string {
  if (!url) return WX_PATH_PREFIX
  if (url.startsWith(WX_PATH_PREFIX + '/') || url === WX_PATH_PREFIX) return url
  return `${WX_PATH_PREFIX}${url.startsWith('/') ? url : '/' + url}`
}