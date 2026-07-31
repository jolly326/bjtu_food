import { API_BASE_URL } from './config'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 401 未登录事件（对齐小程序 uni.$emit('auth:unauthorized')） */
export const AUTH_UNAUTHORIZED = 'auth:unauthorized'
const listeners: Array<() => void> = []
export function onUnauthorized(fn: () => void) {
  listeners.push(fn)
}
function emitUnauthorized() {
  localStorage.removeItem('token')
  listeners.forEach((fn) => fn())
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
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: method !== 'GET' && data ? JSON.stringify(data) : undefined,
      signal: controller.signal,
    })

    clearTimeout(timeout)
    if (!res.ok) {
      // 401 统一处理：清 token + 跳转登录/emit 事件（对齐小程序 §5.x 错误码）
      if (res.status === 401) {
        emitUnauthorized()
        throw new Error('登录已失效，请重新登录')
      }
      throw new Error(`HTTP ${res.status}`)
    }

    const body: ApiResponse<T> = await res.json()
    if (body.code !== 200) {
      // 业务层 401（code===401）同样按未登录处理
      if (body.code === 401) {
        emitUnauthorized()
        throw new Error('登录已失效，请重新登录')
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
