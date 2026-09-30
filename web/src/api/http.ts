/**
 * fetch 封装（web 依赖无 axios）：注入 Bearer 令牌、解析 Result<T>、统一错误处理。
 * 令牌经 setBearerToken 由 auth store 注入（避免与 store 形成模块循环依赖）。
 */
import { API_BASE_URL } from './config'
import { CODE_OK, CODE_UNAUTHORIZED } from '@/types/common'

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

let bearerToken: string | null = null
export function setBearerToken(token: string | null): void {
  bearerToken = token
}
export function clearBearerToken(): void {
  bearerToken = null
}

/** 401 广播名（main.ts 监听并跳转 /login） */
export const UNAUTHORIZED_EVENT = 'auth:unauthorized'

interface RequestOptions {
  /** 请求体为 FormData（文件上传） */
  isForm?: boolean
  /** 自定义超时（毫秒） */
  timeout?: number
}

export async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const url = API_BASE_URL + path
  const headers: Record<string, string> = {}
  if (bearerToken) headers['Authorization'] = `Bearer ${bearerToken}`

  let payload: BodyInit | undefined
  if (body !== undefined) {
    if (options.isForm && body instanceof FormData) {
      payload = body
    } else {
      headers['Content-Type'] = 'application/json'
      payload = JSON.stringify(body)
    }
  }

  const controller = new AbortController()
  const timer = setTimeout(
    () => controller.abort(),
    options.timeout ?? 30000,
  )

  let res: Response
  try {
    res = await fetch(url, {
      method,
      headers,
      body: payload,
      signal: controller.signal,
    })
  } catch {
    clearTimeout(timer)
    throw new ApiError(0, '网络异常，请检查后端是否启动')
  }
  clearTimeout(timer)

  // 401：清令牌并广播，由 main 接管跳转 /login
  if (res.status === CODE_UNAUTHORIZED) {
    clearBearerToken()
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT))
    throw new ApiError(CODE_UNAUTHORIZED, '登录已失效，请重新登录')
  }

  let result: { code: number; message: string; data: T | null }
  try {
    result = await res.json()
  } catch {
    throw new ApiError(res.status, `响应解析失败（HTTP ${res.status}）`)
  }

  if (result.code !== CODE_OK) {
    throw new ApiError(result.code, result.message || `请求失败（${result.code}）`)
  }
  return result.data as T
}

export function get<T>(path: string, params?: object): Promise<T> {
  const qs = toQueryString(params)
  return request<T>('GET', qs ? `${path}?${qs}` : path)
}

export function post<T>(
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return request<T>('POST', path, body, options)
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('PUT', path, body)
}

export function del<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('DELETE', path, body)
}

/** 分页 GET：返回 records 数组（消费方以 records 为准，结束判据自判） */
export function getPage<T>(path: string, params?: object): Promise<T[]> {
  return get<{ records: T[] }>(path, params).then((r) => r.records)
}

function toQueryString(params?: object): string {
  if (!params) return ''
  const parts: string[] = []
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  }
  return parts.join('&')
}
