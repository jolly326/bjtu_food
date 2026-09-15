import { API_BASE_URL } from './config'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 401 未登录事件名（对齐小程序 uni.$emit('auth:unauthorized')）；仅本文件自用，故不导出 */
const AUTH_UNAUTHORIZED = 'auth:unauthorized'
/**
 * 鉴权失效统一广播：通知监听者（不跳转，管理端无登录页）。
 * 导出供 upload.ts 等独立 fetch 通道共用同一失效链路（WEB-107）。
 * WEB-05：onUnauthorized 订阅链路全仓零消费、已删除；emitUnauthorized 仅保留事件广播。
 */
export function emitUnauthorized() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(AUTH_UNAUTHORIZED))
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: any,
): Promise<T> {
  let requestUrl = url
  if (method === 'GET' && data && Object.keys(data).length > 0) {
    const query = new URLSearchParams()
    Object.entries(data).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') query.append(key, String(value))
    })
    const queryString = query.toString()
    if (queryString) requestUrl += `${requestUrl.includes('?') ? '&' : '?'}${queryString}`
  }

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 5000)

  try {
    const res = await fetch(`${API_BASE_URL}${requestUrl}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        // 管理端无登录体系（2026-09-13 定型）：携带环境变量里的管理端口令，由后端 AdminTokenFilter 校验。
        // 口令配置在 web/.env.local 的 VITE_ADMIN_TOKEN（与后端 ADMIN_TOKEN 一致）；仅本地使用，不入库。
        'X-Admin-Token': import.meta.env.VITE_ADMIN_TOKEN || '',
      },
      // GET 不携带 body（fetch 规范 + oxlint no-invalid-fetch-options）
      ...(method !== 'GET' && data ? { body: JSON.stringify(data) } : {}),
      signal: controller.signal,
    })

    clearTimeout(timeout)
    if (!res.ok) {
      // 管理端无登录体系：401/403 均按「口令或鉴权问题」提示（不再跳登录页）
      if (res.status === 401) {
        throw new Error('未授权：请检查管理端口令配置')
      }
      if (res.status === 403) {
        throw new Error('无权限：管理端口令无效或未配置')
      }
      throw new Error(`HTTP ${res.status}`)
    }

    const body: ApiResponse<T> = await res.json()
    if (body.code !== 200) {
      // 业务层 401（code===401）同样按「口令/鉴权失效」处理（管理端无登录页）
      if (body.code === 401) {
        emitUnauthorized()
        throw new Error('未授权：请检查管理端口令配置')
      }
      throw new Error(body.message || '请求失败')
    }
    return body.data
  } catch (e: any) {
    clearTimeout(timeout)
    throw new Error(e.message || '网络异常')
  }
}

export async function get<T>(url: string, data?: any): Promise<T> {
  return request<T>('GET', url, data)
}

export async function post<T>(url: string, data?: any): Promise<T> {
  return request<T>('POST', url, data)
}

export async function put<T>(url: string, data?: any): Promise<T> {
  return request<T>('PUT', url, data)
}

export async function del<T>(url: string): Promise<T> {
  return request<T>('DELETE', url)
}
